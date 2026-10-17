package com.ecommerce.payment.messaging.events;

import java.time.Instant;

public record EventEnvelope<T>(String eventId, String eventType, int schemaVersion,
    String aggregateType, Long aggregateId, Long aggregateVersion, Instant occurredAt,
    String correlationId, T payload) {
  public EventEnvelope {
    if (eventId == null || eventId.isBlank() || eventType == null || eventType.isBlank()
        || schemaVersion != 1 || !"PAYMENT".equals(aggregateType) || aggregateId == null
        || aggregateId <= 0 || aggregateVersion == null || aggregateVersion < 0
        || occurredAt == null || correlationId == null || correlationId.isBlank()
        || correlationId.length() > 128 || payload == null) {
      throw new IllegalArgumentException("PAYMENT_EVENT_ENVELOPE_INVALID");
    }
  }
}
