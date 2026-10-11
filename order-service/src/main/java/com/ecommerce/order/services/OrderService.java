package com.ecommerce.order.services;

import com.ecommerce.order.exceptions.OrderNotFoundException;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderItem;
import com.ecommerce.order.repositories.OrderRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  private final OrderRepository ordersRepository;
  private final CartService cartService;
  private final InventoryService inventoryService;
  private final OutboxEventService outboxEventService;
  private final long paymentTimeoutSeconds;

  public OrderService(
      OrderRepository ordersRepository,
      CartService cartService,
      InventoryService inventoryService,
      OutboxEventService outboxEventService,
      @Value("${app.order.payment-timeout-seconds:900}") long paymentTimeoutSeconds) {
    this.ordersRepository = ordersRepository;
    this.cartService = cartService;
    this.inventoryService = inventoryService;
    this.outboxEventService = outboxEventService;
    if (paymentTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("Thời hạn thanh toán phải lớn hơn 0");
    }
    this.paymentTimeoutSeconds = paymentTimeoutSeconds;
  }

  @Transactional
  public Order createFromCart(Long userId) {
    Cart cart = cartService.getOrCreate(userId);
    if (cart.getItems().isEmpty()) {
      throw new IllegalArgumentException("CART_EMPTY");
    }

    Map<Long, Integer> quantities =
        cart.getItems().stream()
            .collect(
                Collectors.toMap(
                    item -> item.getSkuId(),
                    item -> item.getQuantity(),
                    Integer::sum,
                    LinkedHashMap::new));
    inventoryService.reserve(quantities);

    long totalAmount =
        cart.getItems().stream()
            .mapToLong(item -> item.getDisplayedUnitPrice() * item.getQuantity())
            .sum();
    Order order =
        new Order(userId, totalAmount, "VND", Instant.now().plusSeconds(paymentTimeoutSeconds));
    cart.getItems()
        .forEach(
            item ->
                order.addItem(
                    new OrderItem(
                        item.getSkuId(), item.getQuantity(), item.getDisplayedUnitPrice())));
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
