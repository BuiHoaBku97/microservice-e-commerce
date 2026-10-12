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
    Instant paymentDueAt,
    String failureReason) {
  public static PaymentEvent from(JsonNode payload) {
    return new PaymentEvent(
        text(payload, "eventId"),
        text(payload, "eventType"),
        number(payload, "paymentId"),
        number(payload, "orderId"),
        number(payload, "amount"),
        text(payload, "currency"),
        payload.hasNonNull("paymentDueAt")
            ? Instant.parse(payload.get("paymentDueAt").asText())
            : null,
        text(payload, "failureReason"));
  }

  private static String text(JsonNode payload, String name) {
    return payload.hasNonNull(name) ? payload.get(name).asText() : null;
  }

  private static Long number(JsonNode payload, String name) {
    return payload.hasNonNull(name) ? payload.get(name).asLong() : null;
  }
}
