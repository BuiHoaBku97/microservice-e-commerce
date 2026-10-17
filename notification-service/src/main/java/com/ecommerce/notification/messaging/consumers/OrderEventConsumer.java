package com.ecommerce.notification.messaging.consumers;

import com.ecommerce.notification.configs.CorrelationContext;
import com.ecommerce.notification.messaging.events.NotificationEvent;
import com.ecommerce.notification.services.NotificationService;
import com.ecommerce.notification.services.ProcessedEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {
  private static final Set<String> SUPPORTED_EVENTS = Set.of("OrderCreated", "OrderConfirmed",
      "OrderShipping", "OrderCompleted", "OrderCancelled");
  private final ObjectMapper objectMapper;
  private final NotificationService notificationService;
  private final ProcessedEventService processedEventService;

  public OrderEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
    this(objectMapper, notificationService, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public OrderEventConsumer(ObjectMapper objectMapper, NotificationService notificationService,
      ProcessedEventService processedEventService) {
    this.objectMapper = objectMapper;
    this.notificationService = notificationService;
    this.processedEventService = processedEventService;
  }

  @KafkaListener(topics = "${app.kafka.topics.order-events:ecommerce.order.events}",
      groupId = "${spring.kafka.consumer.group-id:notification-service}")
  public void consume(String message, Acknowledgment acknowledgment) {
    try {
      JsonNode envelope = objectMapper.readTree(message);
      validate(envelope, "ORDER");
      String eventType = envelope.path("eventType").asText();
      if (!SUPPORTED_EVENTS.contains(eventType)) throw new IllegalArgumentException("EVENT_TYPE_UNSUPPORTED");
      CorrelationContext.set(envelope.get("correlationId").asText());
      Runnable action = () -> notificationService.createFromEvent(NotificationEvent.from(envelope));
      if (processedEventService == null) action.run();
      else processedEventService.process("notification-order-events",
          envelope.get("eventId").asText(), eventType, ProcessedEventService.payloadHash(message),
          envelope.get("aggregateId").asLong(), action);
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("ORDER_EVENT_PROCESSING_FAILED", exception);
    } finally {
      CorrelationContext.clear();
    }
  }

  static void validate(JsonNode envelope, String aggregateType) {
    if (envelope.path("schemaVersion").asInt(0) != 1
        || !aggregateType.equals(envelope.path("aggregateType").asText())
        || envelope.path("eventId").asText().isBlank()
        || envelope.path("aggregateId").asLong(0) <= 0
        || envelope.path("aggregateVersion").asLong(-1) < 0
        || !envelope.path("payload").isObject()
        || !CorrelationContext.isValid(envelope.path("correlationId").asText(null)))
      throw new IllegalArgumentException("EVENT_ENVELOPE_INVALID");
    try {
      Instant.parse(envelope.path("occurredAt").asText());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("EVENT_ENVELOPE_INVALID", exception);
    }
  }
}
