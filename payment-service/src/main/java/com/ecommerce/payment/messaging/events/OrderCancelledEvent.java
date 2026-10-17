package com.ecommerce.payment.messaging.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Set;

public record OrderCancelledEvent(
    String eventId,
    Long orderId,
    String reason,
    Instant occurredAt,
    Long aggregateVersion) {
  private static final Set<String> VALID_REASONS =
      Set.of("PAYMENT_FAILED", "PAYMENT_EXPIRED", "CUSTOMER_CANCELLED");

  public OrderCancelledEvent {
    if (eventId == null
        || eventId.isBlank()
        || orderId == null
        || orderId <= 0
        || !VALID_REASONS.contains(reason)
        || occurredAt == null
        || aggregateVersion == null
        || aggregateVersion < 0) {
      throw new IllegalArgumentException("ORDER_CANCELLED_INVALID");
    }
  }

  public static OrderCancelledEvent from(JsonNode event) {
    try {
      return new OrderCancelledEvent(
          event.path("eventId").asText(),
          event.hasNonNull("orderId") ? event.get("orderId").asLong() : null,
          event.path("reason").asText(),
          event.hasNonNull("occurredAt")
              ? Instant.parse(event.get("occurredAt").asText())
              : null,
          event.hasNonNull("aggregateVersion")
              ? event.get("aggregateVersion").asLong()
              : null);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("ORDER_CANCELLED_INVALID", exception);
    }
  }
}
