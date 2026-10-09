package com.ecommerce.order.services;

import com.fasterxml.jackson.databind.JsonNode;

public record PaymentEvent(String eventType, Long orderId) {
  public static PaymentEvent from(JsonNode payload) {
    return new PaymentEvent(payload.path("eventType").asText(), payload.path("orderId").asLong());
  }
}
