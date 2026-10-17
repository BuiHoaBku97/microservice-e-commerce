package com.ecommerce.order.messaging.events;

import java.time.Instant;

public final class OrderEventPayloads {
  private OrderEventPayloads() {}

  public record OrderCreatedPayload(
      Long orderId,
      Long userId,
      Long totalAmount,
      String currency,
      Instant paymentDueAt,
      String orderNumber) {}

  public record OrderConfirmedPayload(
      Long orderId,
      Long userId,
      Long paymentId,
      Long totalAmount,
      String currency,
      Instant confirmedAt) {}

  public record OrderCancelledPayload(
      Long orderId,
      Long userId,
      String reason,
      Instant cancelledAt,
      Instant paymentDueAt) {}

  public record OrderShippingPayload(Long orderId, Long userId, Instant shippingAt) {}

  public record OrderCompletedPayload(
      Long orderId, Long userId, Instant completedAt, Long totalAmount, String currency) {}

  public record OrderConfirmationRejectedPayload(
      Long orderId,
      Long paymentId,
      String reason,
      String currentOrderStatus,
      Instant rejectedAt) {}
}
