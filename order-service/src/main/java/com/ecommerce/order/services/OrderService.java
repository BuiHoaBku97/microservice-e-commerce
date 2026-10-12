package com.ecommerce.order.services;

import com.ecommerce.order.clients.CatalogCheckoutItem;
import com.ecommerce.order.clients.CatalogCheckoutRequest;
import com.ecommerce.order.clients.CatalogClient;
import com.ecommerce.order.exceptions.CatalogUnavailableException;
import com.ecommerce.order.exceptions.OrderNotFoundException;
import com.ecommerce.order.exceptions.PriceChangedException;
import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.CartItem;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderItem;
import com.ecommerce.order.repositories.OrderRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  private final OrderRepository ordersRepository;
  private final CartService cartService;
  private final InventoryService inventoryService;
  private final OutboxEventService outboxEventService;
  private final CatalogClient catalogClient;
  private final long paymentTimeoutSeconds;

  public OrderService(
      OrderRepository ordersRepository,
      CartService cartService,
      InventoryService inventoryService,
      OutboxEventService outboxEventService,
      CatalogClient catalogClient,
      @Value("${app.order.payment-timeout-seconds:900}") long paymentTimeoutSeconds) {
    this.ordersRepository = ordersRepository;
    this.cartService = cartService;
    this.inventoryService = inventoryService;
    this.outboxEventService = outboxEventService;
    this.catalogClient = catalogClient;
    if (paymentTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("Thời hạn thanh toán phải lớn hơn 0");
    }
    this.paymentTimeoutSeconds = paymentTimeoutSeconds;
  }

  @Transactional
  public Order createFromCart(Long userId) {
    return createFromCart(userId, null, null, null);
  }

  @Transactional
  public Order createFromCart(
      Long userId, CartDtos.CheckoutRequest checkoutRequest, String authorization) {
    return createFromCart(
        userId,
        checkoutRequest == null ? null : checkoutRequest.cartVersion(),
        checkoutRequest == null ? null : checkoutRequest.expectedItems(),
        authorization);
  }

  private Order createFromCart(
      Long userId,
      Long expectedCartVersion,
      List<CartDtos.CheckoutItem> expectedItems,
      String authorization) {
    Cart cart = cartService.getOrCreate(userId);
    if (cart.getItems().isEmpty()) {
      throw new IllegalArgumentException("CART_EMPTY");
    }

    if (expectedCartVersion != null && !Objects.equals(expectedCartVersion, cart.getVersion())) {
      throw new IllegalStateException("VERSION_CONFLICT");
    }
    Map<Long, Integer> quantities = cart.getItems().stream()
            .collect(
                Collectors.toMap(
                    CartItem::getSkuId,
                    CartItem::getQuantity,
                    Integer::sum,
                    LinkedHashMap::new));
    List<Long> skuIds = cart.getItems().stream().map(CartItem::getSkuId).distinct().toList();
    Map<Long, CartDtos.CheckoutItem> expectedBySku = new LinkedHashMap<>();
    if (expectedItems != null) {
      for (CartDtos.CheckoutItem expectedItem : expectedItems) {
        expectedBySku.compute(
            expectedItem.skuId(),
            (skuId, previous) -> {
              if (previous == null) {
                return expectedItem;
              }
              if (!Objects.equals(previous.expectedUnitPrice(), expectedItem.expectedUnitPrice())) {
                throw new IllegalStateException("CART_CHANGED");
              }
              return new CartDtos.CheckoutItem(
                  skuId,
                  previous.quantity() + expectedItem.quantity(),
                  previous.expectedUnitPrice());
            });
      }

      boolean differentSkuSet = !expectedBySku.keySet().equals(quantities.keySet());
      boolean differentQuantity =
          expectedBySku.entrySet().stream()
              .anyMatch(
                  entry ->
                      !Objects.equals(
                          entry.getValue().quantity(), quantities.get(entry.getKey())));
      if (differentSkuSet || differentQuantity) {
        throw new IllegalStateException("CART_CHANGED");
      }
    }
    List<CatalogCheckoutItem> catalogItems;
    try {
      catalogItems =
          catalogClient.getCheckoutItems(new CatalogCheckoutRequest(skuIds), authorization);
    } catch (feign.FeignException exception) {
      if (exception.status() == 404 || exception.status() == 409) {
        throw new IllegalStateException("SKU_NOT_SELLABLE");
      }
      throw new CatalogUnavailableException();
    }
    Map<Long, CatalogCheckoutItem> catalogBySku =
        catalogItems.stream()
            .collect(Collectors.toMap(CatalogCheckoutItem::skuId, item -> item));
    if (catalogBySku.size() != skuIds.size()
        || catalogBySku.values().stream().anyMatch(item -> !item.sellable())) {
      throw new IllegalStateException("SKU_NOT_SELLABLE");
    }
    for (var cartItem : cart.getItems()) {
      CatalogCheckoutItem catalogItem = catalogBySku.get(cartItem.getSkuId());
      if (!Objects.equals(catalogItem.unitPrice(), cartItem.getDisplayedUnitPrice())
          || (expectedBySku.containsKey(catalogItem.skuId())
              && !Objects.equals(
                  expectedBySku.get(catalogItem.skuId()).expectedUnitPrice(),
                  catalogItem.unitPrice()))) {
        throw new PriceChangedException();
      }
    }
    inventoryService.reserve(quantities);

    long totalAmount =
        cart.getItems().stream()
            .mapToLong(item -> catalogBySku.get(item.getSkuId()).unitPrice() * item.getQuantity())
            .sum();
    Order order =
        new Order(userId, totalAmount, "VND", Instant.now().plusSeconds(paymentTimeoutSeconds));
    cart.getItems()
        .forEach(
            item ->
                order.addItem(
                    new OrderItem(
                        item.getSkuId(),
                        item.getQuantity(),
                        catalogBySku.get(item.getSkuId()).unitPrice())));
    cartService.clear(userId, cart.getVersion());
    Order saved = ordersRepository.save(order);
    outboxEventService.enqueueOrderCreated(saved);
    return saved;
  }

  @Transactional
  public List<Order> listByUser(Long userId) {
    return ordersRepository.findByUserIdOrderByCreatedAtDesc(userId);
  }

  @Transactional
  public Order getByUser(Long userId, Long orderId) {
    return ordersRepository
        .findByIdAndUserId(orderId, userId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  @Transactional
  public Order cancel(Long userId, Long orderId) {
    Order order = getByUser(userId, orderId);
    Map<Long, Integer> quantities =
        order.getItems().stream()
            .collect(
                Collectors.toMap(
                    item -> item.getSkuId(),
                    item -> item.getQuantity(),
                    Integer::sum,
                    LinkedHashMap::new));
    inventoryService.release(quantities);
    order.cancel();
    Order saved = ordersRepository.save(order);
    outboxEventService.enqueueOrderCancelled(saved);
    return saved;
  }

  @Transactional
  public Order handlePaymentCompleted(Long orderId) {
    Order order = get(orderId);
    order.markPaid();
    return ordersRepository.save(order);
  }

  @Transactional
  public Order handlePaymentFailed(Long orderId) {
    Order order = get(orderId);
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.PENDING) {
      inventoryService.release(quantities(order));
      order.markPaymentFailed();
    }
    return ordersRepository.save(order);
  }

  private Order get(Long orderId) {
    return ordersRepository
        .findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  private Map<Long, Integer> quantities(Order order) {
    return order.getItems().stream()
        .collect(
            Collectors.toMap(
                OrderItem::getSkuId, OrderItem::getQuantity, Integer::sum, LinkedHashMap::new));
  }
}
