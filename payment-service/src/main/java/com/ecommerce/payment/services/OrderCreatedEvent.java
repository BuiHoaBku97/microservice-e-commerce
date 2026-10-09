package com.ecommerce.payment.services;

import com.fasterxml.jackson.databind.JsonNode;

public record OrderCreatedEvent(Long orderId, Long userId, Long totalAmount, String currency) {
  public static OrderCreatedEvent from(JsonNode payload) {
    return new OrderCreatedEvent(
        payload.path("orderId").asLong(),
        payload.path("userId").asLong(),
        payload.hasNonNull("totalAmount") ? payload.get("totalAmount").asLong() : null,
        payload.path("currency").asText("VND"));
  }
}
