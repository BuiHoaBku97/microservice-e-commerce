package com.ecommerce.order.messaging.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record PaymentEvent(
    String eventId,
    String eventType,
    Long paymentId,
    Long orderId,
    Long amount,
    String currency,
    String failureReason) {
  public PaymentEvent(
      String eventId,
      String eventType,
      Long paymentId,
      Long orderId,
      Long amount,
      String currency,
      Instant ignoredPaymentDueAt,
      String failureReason) {
    this(eventId, eventType, paymentId, orderId, amount, currency, failureReason);
  }

  public static PaymentEvent from(JsonNode envelope) {
    JsonNode payload = envelope.path("payload");
    return new PaymentEvent(
        text(envelope, "eventId"),
        text(envelope, "eventType"),
        number(payload, "paymentId"),
        number(payload, "orderId"),
        number(payload, "amount"),
        text(payload, "currency"),
        text(payload, "reason"));
  }

  private static String text(JsonNode payload, String name) {
    return payload.hasNonNull(name) ? payload.get(name).asText() : null;
  }

  private static Long number(JsonNode payload, String name) {
    return payload.hasNonNull(name) ? payload.get(name).asLong() : null;
  }
}
