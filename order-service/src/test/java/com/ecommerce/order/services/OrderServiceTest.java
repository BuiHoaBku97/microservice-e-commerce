package com.ecommerce.order.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.CartItem;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderStatus;
import com.ecommerce.order.repositories.OrderRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
  @Mock private OrderRepository orderRepository;
  @Mock private CartService cartService;
  @Mock private InventoryService inventoryService;
  @Mock private OutboxEventService outboxEventService;

  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderService =
        new OrderService(orderRepository, cartService, inventoryService, outboxEventService, 900);
  }

  @Test
  void createFromCart_shouldCreateOrderAndReserveInventory() {
    Cart cart = cartWithItem(10L, 2, 150_000L);
    when(cartService.getOrCreate(7L)).thenReturn(cart);
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Order order = orderService.createFromCart(7L);

    assertEquals(300_000L, order.getTotalAmount());
    assertEquals(1, order.getItems().size());
    org.assertj.core.api.Assertions.assertThat(order.getPaymentDueAt())
        .isBetween(
            java.time.Instant.now().plusSeconds(895), java.time.Instant.now().plusSeconds(900));
    verify(inventoryService).reserve(Map.of(10L, 2));
    verify(outboxEventService).enqueueOrderCreated(any(Order.class));
    verify(cartService).clear(eq(7L), eq(cart.getVersion()));
  }

  @Test
  void cancel_shouldReleaseInventoryAndChangeStatus() {
    Order order = new Order(7L, 300_000L, "VND");
    order.addItem(new com.ecommerce.order.models.entities.OrderItem(10L, 2, 150_000L));
    when(orderRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(order));
    when(orderRepository.save(order)).thenReturn(order);

    Order cancelled = orderService.cancel(7L, 3L);

    assertEquals("CANCELLED", cancelled.getStatus().name());
    verify(inventoryService).release(Map.of(10L, 2));
    verify(outboxEventService).enqueueOrderCancelled(order);
    verify(orderRepository).save(order);
  }

  @Test
  void handlePaymentCompleted_shouldMarkOrderAsPaid() {
    Order order = new Order(7L, 300_000L, "VND");
    when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
    when(orderRepository.save(order)).thenReturn(order);

    Order paid = orderService.handlePaymentCompleted(3L);

    assertEquals(OrderStatus.PAID, paid.getStatus());
    verify(orderRepository).save(order);
  }

  @Test
  void handlePaymentFailed_shouldReleaseInventoryOnlyOnce() {
    Order order = new Order(7L, 300_000L, "VND");
    order.addItem(new com.ecommerce.order.models.entities.OrderItem(10L, 2, 150_000L));
    when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
    when(orderRepository.save(order)).thenReturn(order);

    Order failed = orderService.handlePaymentFailed(3L);

    assertEquals(OrderStatus.PAYMENT_FAILED, failed.getStatus());
    verify(inventoryService).release(Map.of(10L, 2));
  }

  private Cart cartWithItem(Long skuId, int quantity, long unitPrice) {
    Cart cart = new Cart();
    cart.setUserId(7L);
    CartItem item = new CartItem();
    item.setCart(cart);
    item.setSkuId(skuId);
    item.setQuantity(quantity);
    item.setDisplayedUnitPrice(unitPrice);
    cart.getItems().add(item);
    cart.touch();
    return cart;
  }
}
