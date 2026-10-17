package com.ecommerce.payment.messaging.events;

import java.time.Instant;

public final class PaymentEventPayloads {
  private PaymentEventPayloads() {}

  public record PaymentSucceededPayload(Long paymentId, Long orderId, Long userId, Long amount,
      String currency, String provider, String providerTransactionId, Instant paidAt) {}

  public record PaymentFailedPayload(Long paymentId, Long orderId, Long userId, Long amount,
      String currency, Instant failedAt, String reason) {}
}
