package com.ecommerce.order.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.CartItem;
import com.ecommerce.order.models.entities.Order;
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
  @Mock private OrderRepository orders;
  @Mock private CartService carts;
  @Mock private InventoryService inventory;
  @Mock private OutboxEventService outbox;

  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderService = new OrderService(orders, carts, inventory, outbox);
  }

  @Test
  void createFromCart_shouldCreateOrderAndReserveInventory() {
    Cart cart = cartWithItem(10L, 2, 150_000L);
    when(carts.getOrCreate(7L)).thenReturn(cart);
    when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Order order = orderService.createFromCart(7L);

    assertEquals(300_000L, order.getTotalAmount());
    assertEquals(1, order.getItems().size());
    verify(inventory).reserve(Map.of(10L, 2));
    verify(outbox).enqueueOrderCreated(any(Order.class));
    verify(carts).clear(eq(7L), eq(cart.getVersion()));
  }

  @Test
  void cancel_shouldReleaseInventoryAndChangeStatus() {
    Order order = new Order(7L, 300_000L, "VND");
    order.addItem(new com.ecommerce.order.models.entities.OrderItem(10L, 2, 150_000L));
    when(orders.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(order));
    when(orders.save(order)).thenReturn(order);

    Order cancelled = orderService.cancel(7L, 3L);

    assertEquals("CANCELLED", cancelled.getStatus().name());
    verify(inventory).release(Map.of(10L, 2));
    verify(outbox).enqueueOrderCancelled(order);
    verify(orders).save(order);
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
