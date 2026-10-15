package com.ecommerce.order.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.clients.CatalogCheckoutItem;
import com.ecommerce.order.clients.CatalogClient;
import com.ecommerce.order.clients.IdentityCheckoutContext;
import com.ecommerce.order.clients.IdentityClient;
import com.ecommerce.order.messaging.events.PaymentEvent;
import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.CartItem;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderStatus;
import com.ecommerce.order.repositories.OrderRepository;
import com.ecommerce.order.repositories.PaymentEventHistoryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
  @Mock private OrderRepository orderRepository;
  @Mock private CartService cartService;
  @Mock private InventoryService inventoryService;
  @Mock private OutboxEventService outboxEventService;
  @Mock private CatalogClient catalogClient;
  @Mock private IdentityClient identityClient;
  @Mock private PaymentEventHistoryRepository paymentEventHistoryRepository;

  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderService =
        new OrderService(
            orderRepository,
            cartService,
            inventoryService,
            outboxEventService,
            catalogClient,
            identityClient,
            900);
  }

  @Test
  void createFromCart_shouldCreateOrderAndReserveInventory() {
    Cart cart = cartWithItem(10L, 2, 150_000L);
    when(cartService.getOrCreate(7L)).thenReturn(cart);
    when(catalogClient.getCheckoutItems(any(), any()))
        .thenReturn(
            List.of(
                new CatalogCheckoutItem(
                    10L, 20L, "SKU-10", "Sản phẩm", "RED", "M", 150_000L, "VND", 1L, true)));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Order order = orderService.createFromCart(7L);

    assertEquals(300_000L, order.getTotalAmount());
    assertEquals(1, order.getItems().size());
    org.assertj.core.api.Assertions.assertThat(order.getPaymentDueAt())
        .isBetween(
            java.time.Instant.now().plusSeconds(895), java.time.Instant.now().plusSeconds(900));
    verify(inventoryService).reserveInCurrentTransaction(any(), eq(Map.of(10L, 2)), any(), any());
    verify(outboxEventService).enqueueOrderCreated(any(Order.class));
    verify(cartService).clear(eq(7L), eq(cart.getVersion()));
  }

  @Test
  void createFromCart_shouldRejectClientPriceBeforeReservingInventory() {
    Cart cart = cartWithItem(10L, 2, 150_000L);
    when(cartService.getOrCreate(7L)).thenReturn(cart);
    when(catalogClient.getCheckoutItems(any(), any()))
        .thenReturn(
            List.of(
                new CatalogCheckoutItem(
                    10L, 20L, "SKU-10", "Sản phẩm", "RED", "M", 180_000L, "VND", 1L, true)));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.createFromCart(7L))
        .isInstanceOf(com.ecommerce.order.exceptions.PriceChangedException.class);
    verify(inventoryService, never()).executeCommand(any(), any());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createFromCart_shouldRejectInactiveSkuBeforeReservingInventory() {
    Cart cart = cartWithItem(10L, 2, 150_000L);
    when(cartService.getOrCreate(7L)).thenReturn(cart);
    when(catalogClient.getCheckoutItems(any(), any()))
        .thenReturn(
            List.of(
                new CatalogCheckoutItem(
                    10L, 20L, "SKU-10", "Sản phẩm", "RED", "M", 150_000L, "VND", 1L, false)));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.createFromCart(7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("SKU_NOT_SELLABLE");
    verify(inventoryService, never()).executeCommand(any(), any());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void checkout_shouldReplaySameOrderForSameIdempotencyKey() {
    Cart cart = cartWithItem(10L, 2, 150_000L);
    CartDtos.CheckoutRequest request =
        new CartDtos.CheckoutRequest(
            cart.getVersion(), 1L, List.of(new CartDtos.CheckoutItem(10L, 2, 150_000L)));
    when(orderRepository.findByUserIdAndIdempotencyKey(7L, "checkout-1"))
        .thenReturn(Optional.empty(), Optional.empty());
    when(cartService.getForCheckout(7L)).thenReturn(cart);
    when(identityClient.getCheckoutContext(any(), any()))
        .thenReturn(
            new IdentityCheckoutContext(
                7L,
                "ACTIVE",
                new IdentityCheckoutContext.AddressSnapshot(
                    1L,
                    "Nguyễn Văn A",
                    "0900000000",
                    "1 Đường A",
                    "Phường 1",
                    "Quận 1",
                    "TP HCM")));
    when(catalogClient.getCheckoutItems(any(), any()))
        .thenReturn(
            List.of(
                new CatalogCheckoutItem(
                    10L, 20L, "SKU-10", "Sản phẩm", "RED", "M", 150_000L, "VND", 1L, true)));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    OrderService.CheckoutResult first =
        orderService.checkout(7L, request, "Bearer token", "checkout-1");
    when(orderRepository.findByUserIdAndIdempotencyKey(7L, "checkout-1"))
        .thenReturn(Optional.of(first.order()));
    OrderService.CheckoutResult replay =
        orderService.checkout(7L, request, "Bearer token", "checkout-1");

    org.assertj.core.api.Assertions.assertThat(first.replayed()).isFalse();
    org.assertj.core.api.Assertions.assertThat(first.order().getShippingAddressId()).isEqualTo(1L);
    org.assertj.core.api.Assertions.assertThat(first.order().getShippingProvince())
        .isEqualTo("TP HCM");
    org.assertj.core.api.Assertions.assertThat(replay.replayed()).isTrue();
    org.assertj.core.api.Assertions.assertThat(replay.order()).isSameAs(first.order());
    verify(inventoryService).reserveInCurrentTransaction(any(), eq(Map.of(10L, 2)), any(), any());
    verify(cartService).clear(eq(7L), eq(cart.getVersion()));
  }

  @Test
  void cancel_shouldReleaseInventoryAndChangeStatus() {
    Order order = new Order(7L, 300_000L, "VND");
    ReflectionTestUtils.setField(order, "id", 3L);
    order.addItem(new com.ecommerce.order.models.entities.OrderItem(10L, 2, 150_000L));
    when(orderRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(order));
    when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
    when(orderRepository.save(order)).thenReturn(order);

    Order cancelled = orderService.cancel(7L, 3L);

    assertEquals("CANCELLED", cancelled.getStatus().name());
    verify(inventoryService).releaseInCurrentTransaction(eq(3L), eq("CUSTOMER_CANCELLED"), any());
    verify(outboxEventService).enqueueOrderCancelled(order);
    verify(orderRepository).save(order);
  }

  @Test
  void handlePaymentFailed_shouldReleaseInventoryOnlyOnce() {
    Order order = new Order(7L, 300_000L, "VND", java.time.Instant.now().plusSeconds(60));
    order.addItem(new com.ecommerce.order.models.entities.OrderItem(10L, 2, 150_000L));
    when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
    when(paymentEventHistoryRepository.existsByEventId("evt-failed")).thenReturn(false);
    when(paymentEventHistoryRepository.existsByPaymentId(24L)).thenReturn(false);
    when(orderRepository.save(order)).thenReturn(order);

    Order failed =
        orderServiceWithHistory()
            .handlePaymentFailed(
                new PaymentEvent(
                    "evt-failed",
                    "PaymentFailed",
                    24L,
                    3L,
                    300_000L,
                    "VND",
                    order.getPaymentDueAt(),
                    "provider_declined"));

    assertEquals(OrderStatus.CANCELLED, failed.getStatus());
    verify(inventoryService).releaseInCurrentTransaction(eq(3L), eq("PAYMENT_FAILED"), any());
    verify(outboxEventService).enqueueOrderCancelled(order);
  }

  @Test
  void handlePaymentSucceeded_shouldFinalizeInventoryAndConfirmOrder() {
    Order order = new Order(7L, 300_000L, "VND", java.time.Instant.now().plusSeconds(60));
    order.addItem(new com.ecommerce.order.models.entities.OrderItem(10L, 2, 150_000L));
    when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
    when(paymentEventHistoryRepository.existsByEventId("evt-1")).thenReturn(false);
    when(paymentEventHistoryRepository.existsByPaymentId(20L)).thenReturn(false);
    when(orderRepository.save(order)).thenReturn(order);

    Order confirmed =
        orderServiceWithHistory()
            .handlePaymentSucceeded(
                new PaymentEvent(
                    "evt-1",
                    "PaymentSucceeded",
                    20L,
                    3L,
                    300_000L,
                    "VND",
                    order.getPaymentDueAt(),
                    null));

    assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
    verify(inventoryService).commitInCurrentTransaction(eq(3L), eq("evt-1"), any());
    verify(outboxEventService).enqueueOrderConfirmed(order);
    verify(paymentEventHistoryRepository).save(any());
  }

  @Test
  void handlePaymentSucceeded_shouldCancelAndReleaseWhenAmountIsWrong() {
    Order order = new Order(7L, 300_000L, "VND", java.time.Instant.now().plusSeconds(60));
    order.addItem(new com.ecommerce.order.models.entities.OrderItem(10L, 2, 150_000L));
    when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
    when(paymentEventHistoryRepository.existsByEventId("evt-2")).thenReturn(false);
    when(paymentEventHistoryRepository.existsByPaymentId(21L)).thenReturn(false);
    when(orderRepository.save(order)).thenReturn(order);

    Order reviewed =
        orderServiceWithHistory()
            .handlePaymentSucceeded(
                new PaymentEvent(
                    "evt-2",
                    "PaymentSucceeded",
                    21L,
                    3L,
                    1L,
                    "VND",
                    order.getPaymentDueAt(),
                    null));

    assertEquals(OrderStatus.CANCELLED, reviewed.getStatus());
    assertEquals("PAYMENT_MISMATCH", reviewed.getCancellationReason());
    verify(inventoryService)
        .releaseInCurrentTransaction(eq(3L), eq("PAYMENT_MISMATCH"), any());
    verify(inventoryService, never()).commitInCurrentTransaction(any(), any(), any());
    verify(outboxEventService).enqueueOrderCancelled(order);
    verify(outboxEventService)
        .enqueueOrderConfirmationRejected(order, 21L, "PAYMENT_MISMATCH");
  }

  @Test
  void handlePaymentSucceeded_shouldReviewLateCallbackAndNeverFinalize() {
    Order order = new Order(7L, 300_000L, "VND", java.time.Instant.now().minusSeconds(1));
    order.addItem(new com.ecommerce.order.models.entities.OrderItem(10L, 2, 150_000L));
    when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
    when(paymentEventHistoryRepository.existsByEventId("evt-late")).thenReturn(false);
    when(paymentEventHistoryRepository.existsByPaymentId(23L)).thenReturn(false);
    when(orderRepository.save(order)).thenReturn(order);

    Order reviewed =
        orderServiceWithHistory()
            .handlePaymentSucceeded(
                new PaymentEvent(
                    "evt-late",
                    "PaymentSucceeded",
                    23L,
                    3L,
                    300_000L,
                    "VND",
                    order.getPaymentDueAt(),
                    null));

    assertEquals(OrderStatus.CANCELLED, reviewed.getStatus());
    verify(inventoryService).releaseInCurrentTransaction(eq(3L), eq("PAYMENT_EXPIRED"), any());
    verify(inventoryService, never()).commitInCurrentTransaction(any(), any(), any());
  }

  @Test
  void handlePaymentSucceeded_shouldIgnoreReplayAfterFirstHistoryRecord() {
    Order order = new Order(7L, 300_000L, "VND", java.time.Instant.now().plusSeconds(60));
    when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
    when(paymentEventHistoryRepository.existsByEventId("evt-3")).thenReturn(true);

    orderServiceWithHistory()
        .handlePaymentSucceeded(
            new PaymentEvent(
                "evt-3",
                "PaymentSucceeded",
                22L,
                3L,
                300_000L,
                "VND",
                order.getPaymentDueAt(),
                null));

    verify(inventoryService, never()).commitInCurrentTransaction(any(), any(), any());
    verify(paymentEventHistoryRepository, never()).save(any());
  }

  @Test
  void expirePendingPayments_shouldReleaseInventoryAndRejectExpiredOrder() {
    java.time.Instant now = java.time.Instant.now();
    Order order = new Order(7L, 300_000L, "VND", now.minusSeconds(1));
    ReflectionTestUtils.setField(order, "id", 3L);
    order.addItem(new com.ecommerce.order.models.entities.OrderItem(10L, 2, 150_000L));
    when(orderRepository.findExpiredPendingOrders(OrderStatus.PENDING_PAYMENT, now))
        .thenReturn(List.of(order));
    when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
    when(orderRepository.save(order)).thenReturn(order);

    int expired = orderService.expirePendingPayments(now);

    assertEquals(1, expired);
    assertEquals(OrderStatus.CANCELLED, order.getStatus());
    verify(inventoryService).releaseInCurrentTransaction(eq(3L), eq("PAYMENT_EXPIRED"), any());
    verify(outboxEventService).enqueueOrderCancelled(order);
  }

  private OrderService orderServiceWithHistory() {
    return new OrderService(
        orderRepository,
        cartService,
        inventoryService,
        outboxEventService,
        catalogClient,
        identityClient,
        paymentEventHistoryRepository,
        900);
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
