package com.ecommerce.payment.messaging.consumers;

import com.ecommerce.payment.configs.CorrelationContext;
import com.ecommerce.payment.messaging.events.OrderCancelledEvent;
import com.ecommerce.payment.messaging.events.OrderCreatedEvent;
import com.ecommerce.payment.services.PaymentService;
import com.ecommerce.payment.services.ProcessedEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {
  private final ObjectMapper objectMapper;
  private final PaymentService paymentService;
  private final ProcessedEventService processedEventService;

  public OrderEventConsumer(ObjectMapper objectMapper, PaymentService paymentService) {
    this(objectMapper, paymentService, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public OrderEventConsumer(ObjectMapper objectMapper, PaymentService paymentService,
      ProcessedEventService processedEventService) {
    this.objectMapper = objectMapper;
    this.paymentService = paymentService;
    this.processedEventService = processedEventService;
  }

  @KafkaListener(topics = "${app.kafka.topics.order-events:ecommerce.order.events}",
      groupId = "${spring.kafka.consumer.group-id:payment-service}")
  public void consume(String message, Acknowledgment acknowledgment) {
    try {
      JsonNode envelope = objectMapper.readTree(message);
      validateEnvelope(envelope);
      CorrelationContext.set(envelope.get("correlationId").asText());
      Runnable action = () -> handle(envelope);
      if (processedEventService == null) action.run();
      else processedEventService.process("payment-order-events",
          envelope.get("eventId").asText(), envelope.get("eventType").asText(), action);
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("Khong the xu ly su kien don hang", exception);
    } finally {
      CorrelationContext.clear();
    }
  }

  private void handle(JsonNode envelope) {
    JsonNode payload = envelope.path("payload");
    switch (envelope.path("eventType").asText()) {
      case "OrderCreated" -> paymentService.processOrderPayment(OrderCreatedEvent.from(envelope));
      case "OrderConfirmed" -> paymentService.handleOrderConfirmed(
          requiredLong(payload, "orderId"), requiredLong(payload, "paymentId"));
      case "OrderCancelled" -> paymentService.handleOrderCancelled(OrderCancelledEvent.from(envelope));
      case "OrderConfirmationRejected" -> paymentService.handleOrderConfirmationRejected(
          requiredLong(payload, "orderId"), requiredLong(payload, "paymentId"),
          requiredText(payload, "reason"));
      case "OrderShipping", "OrderCompleted" -> { }
      default -> throw new IllegalArgumentException("ORDER_EVENT_TYPE_UNSUPPORTED");
    }
  }

  private void validateEnvelope(JsonNode envelope) {
    JsonNode payload = envelope.path("payload");
    if (envelope.path("eventId").asText().isBlank()
        || envelope.path("eventType").asText().isBlank()
        || envelope.path("schemaVersion").asInt(0) != 1
        || !"ORDER".equals(envelope.path("aggregateType").asText())
        || envelope.path("aggregateId").asLong(0) <= 0
        || envelope.path("aggregateVersion").asLong(-1) < 0
        || !payload.isObject()
        || requiredLong(payload, "orderId") != envelope.path("aggregateId").asLong()
        || !CorrelationContext.isValid(envelope.path("correlationId").asText(null))) {
      throw new IllegalArgumentException("ORDER_EVENT_ENVELOPE_INVALID");
    }
    try { Instant.parse(envelope.path("occurredAt").asText()); }
    catch (Exception exception) {
      throw new IllegalArgumentException("ORDER_EVENT_OCCURRED_AT_INVALID", exception);
    }
    switch (envelope.path("eventType").asText()) {
      case "OrderCreated" -> {
        requiredLong(payload, "userId");
        requiredLong(payload, "totalAmount");
        requiredText(payload, "currency");
        requiredText(payload, "orderNumber");
        requiredInstant(payload, "paymentDueAt");
      }
      case "OrderConfirmed" -> {
        requiredLong(payload, "userId");
        requiredLong(payload, "paymentId");
        requiredLong(payload, "totalAmount");
        requiredText(payload, "currency");
        requiredInstant(payload, "confirmedAt");
      }
      case "OrderCancelled" -> {
        requiredLong(payload, "userId");
        requiredText(payload, "reason");
        requiredInstant(payload, "cancelledAt");
        requiredInstant(payload, "paymentDueAt");
      }
      case "OrderConfirmationRejected" -> {
        requiredLong(payload, "paymentId");
        requiredText(payload, "reason");
        requiredText(payload, "currentOrderStatus");
        requiredInstant(payload, "rejectedAt");
      }
      case "OrderShipping" -> {
        requiredLong(payload, "userId");
        requiredInstant(payload, "shippingAt");
      }
      case "OrderCompleted" -> {
        requiredLong(payload, "userId");
        requiredLong(payload, "totalAmount");
        requiredText(payload, "currency");
        requiredInstant(payload, "completedAt");
      }
      default -> throw new IllegalArgumentException("ORDER_EVENT_TYPE_UNSUPPORTED");
    }
  }

  private long requiredLong(JsonNode node, String name) {
    if (!node.has(name) || !node.get(name).isIntegralNumber() || node.get(name).asLong() <= 0)
      throw new IllegalArgumentException("ORDER_EVENT_FIELD_INVALID:" + name);
    return node.get(name).asLong();
  }

  private String requiredText(JsonNode node, String name) {
    if (!node.hasNonNull(name) || node.get(name).asText().isBlank())
      throw new IllegalArgumentException("ORDER_EVENT_FIELD_INVALID:" + name);
    return node.get(name).asText();
  }

  private Instant requiredInstant(JsonNode node, String name) {
    try {
      return Instant.parse(requiredText(node, name));
    } catch (Exception exception) {
      throw new IllegalArgumentException("ORDER_EVENT_FIELD_INVALID:" + name, exception);
    }
  }
}
