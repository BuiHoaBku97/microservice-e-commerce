package com.ecommerce.payment.messaging.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record OrderCreatedEvent(
    Long orderId, Long userId, Long totalAmount, String currency, Instant paymentDueAt) {
  public OrderCreatedEvent(Long orderId, Long userId, Long totalAmount, String currency) {
    this(orderId, userId, totalAmount, currency, null);
  }

  public static OrderCreatedEvent from(JsonNode envelope) {
    JsonNode payload = envelope.path("payload");
    return new OrderCreatedEvent(
        payload.path("orderId").asLong(),
        payload.path("userId").asLong(),
        payload.hasNonNull("totalAmount") ? payload.get("totalAmount").asLong() : null,
        payload.path("currency").asText("VND"),
        payload.hasNonNull("paymentDueAt")
            ? Instant.parse(payload.get("paymentDueAt").asText())
            : null);
  }
}
