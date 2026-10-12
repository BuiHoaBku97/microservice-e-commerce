package com.ecommerce.order.services;

import com.ecommerce.order.clients.CatalogCheckoutItem;
import com.ecommerce.order.clients.CatalogCheckoutRequest;
import com.ecommerce.order.clients.CatalogClient;
import com.ecommerce.order.exceptions.CatalogUnavailableException;
import com.ecommerce.order.exceptions.IdempotencyKeyConflictException;
import com.ecommerce.order.exceptions.OrderNotFoundException;
import com.ecommerce.order.exceptions.PriceChangedException;
import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.CartItem;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderItem;
import com.ecommerce.order.repositories.OrderRepository;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  public record CheckoutResult(Order order, boolean replayed) {}

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
    Cart cart = cartService.getOrCreate(userId);
    return createFromCart(cart, userId, null, null, null, null, null);
  }

  @Transactional
  public Order createFromCart(
      Long userId, CartDtos.CheckoutRequest checkoutRequest, String authorization) {
    Cart cart = cartService.getOrCreate(userId);
    return createFromCart(
        cart,
        userId,
        checkoutRequest == null ? null : checkoutRequest.cartVersion(),
        checkoutRequest == null ? null : checkoutRequest.expectedItems(),
        authorization,
        null,
        null);
  }

  @Transactional
  public CheckoutResult checkout(
      Long userId,
      CartDtos.CheckoutRequest checkoutRequest,
      String authorization,
      String idempotencyKey) {
    String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
    String requestHash = checkoutHash(checkoutRequest);
    Order existing =
        ordersRepository.findByUserIdAndIdempotencyKey(userId, normalizedKey).orElse(null);
    if (existing != null) {
      return replayOrReject(existing, requestHash);
    }

    Cart cart = cartService.getForCheckout(userId);
    existing = ordersRepository.findByUserIdAndIdempotencyKey(userId, normalizedKey).orElse(null);
    if (existing != null) {
      return replayOrReject(existing, requestHash);
    }

    return new CheckoutResult(
        createFromCart(
            cart,
            userId,
            checkoutRequest.cartVersion(),
            checkoutRequest.expectedItems(),
            authorization,
            normalizedKey,
            requestHash),
        false);
  }

  private Order createFromCart(
      Cart cart,
      Long userId,
      Long expectedCartVersion,
      List<CartDtos.CheckoutItem> expectedItems,
      String authorization,
      String idempotencyKey,
      String idempotencyHash) {
    if (cart.getItems().isEmpty()) {
      throw new IllegalArgumentException("CART_EMPTY");
    }

    if (expectedCartVersion != null && !Objects.equals(expectedCartVersion, cart.getVersion())) {
      throw new IllegalStateException("VERSION_CONFLICT");
    }
    Map<Long, Integer> quantities =
        cart.getItems().stream()
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
    if (idempotencyKey != null) {
      order.setIdempotency(idempotencyKey, idempotencyHash);
    }
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

  private CheckoutResult replayOrReject(Order existing, String requestHash) {
    if (!Objects.equals(existing.getIdempotencyHash(), requestHash)) {
      throw new IdempotencyKeyConflictException();
    }
    return new CheckoutResult(existing, true);
  }

  private String normalizeIdempotencyKey(String idempotencyKey) {
    String normalizedKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    if (normalizedKey.isEmpty()) {
      throw new IllegalArgumentException("Idempotency-Key là bắt buộc");
    }
    if (normalizedKey.length() > 100) {
      throw new IllegalArgumentException("Idempotency-Key không được vượt quá 100 ký tự");
    }
    return normalizedKey;
  }

  private String checkoutHash(CartDtos.CheckoutRequest request) {
    String canonical =
        request.cartVersion()
            + "|"
            + request.expectedItems().stream()
                .sorted(Comparator.comparing(CartDtos.CheckoutItem::skuId))
                .map(
                    item ->
                        item.skuId()
                            + ":"
                            + item.quantity()
                            + ":"
                            + item.expectedUnitPrice())
                .collect(Collectors.joining(";"));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Không thể tạo mã xác nhận checkout", exception);
    }
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
