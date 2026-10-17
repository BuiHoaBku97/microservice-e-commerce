package com.ecommerce.order.messaging.consumers;

import com.ecommerce.order.configs.CorrelationContext;
import com.ecommerce.order.messaging.events.PaymentEvent;
import com.ecommerce.order.services.OrderService;
import com.ecommerce.order.services.ProcessedEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {
  private final ObjectMapper objectMapper;
  private final OrderService orderService;
  private final ProcessedEventService processedEventService;

  public PaymentEventConsumer(ObjectMapper objectMapper, OrderService orderService) {
    this(objectMapper, orderService, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public PaymentEventConsumer(ObjectMapper objectMapper, OrderService orderService,
      ProcessedEventService processedEventService) {
    this.objectMapper = objectMapper;
    this.orderService = orderService;
    this.processedEventService = processedEventService;
  }

  @KafkaListener(topics = "${app.kafka.topics.payment-events:ecommerce.payment.events}",
      groupId = "${spring.kafka.consumer.group-id:order-service}")
  public void consume(String message, Acknowledgment acknowledgment) {
    try {
      JsonNode envelope = objectMapper.readTree(message);
      PaymentEvent event = PaymentEvent.from(envelope);
      validateEnvelope(envelope, event);
      CorrelationContext.set(envelope.get("correlationId").asText());
      Runnable action = () -> handle(event);
      if (processedEventService == null) action.run();
      else processedEventService.process("order-payment-events", event.eventId(), event.eventType(), action);
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("Khong the xu ly su kien thanh toan", exception);
    } finally {
      CorrelationContext.clear();
    }
  }

  private void handle(PaymentEvent event) {
    if ("PaymentSucceeded".equals(event.eventType())) orderService.handlePaymentSucceeded(event);
    else if ("PaymentFailed".equals(event.eventType())) orderService.handlePaymentFailed(event);
    else throw new IllegalArgumentException("PAYMENT_EVENT_TYPE_UNSUPPORTED");
  }

  private void validateEnvelope(JsonNode envelope, PaymentEvent event) {
    JsonNode payload = envelope.path("payload");
    if (event.eventId() == null || event.eventId().isBlank()
        || event.eventType() == null || event.eventType().isBlank()
        || envelope.path("schemaVersion").asInt(0) != 1
        || !"PAYMENT".equals(envelope.path("aggregateType").asText())
        || event.paymentId() == null || event.paymentId() <= 0
        || envelope.path("aggregateId").asLong(0) != event.paymentId()
        || envelope.path("aggregateVersion").asLong(-1) < 0
        || !payload.isObject()
        || event.orderId() == null || event.orderId() <= 0
        || event.amount() == null || event.amount() <= 0
        || event.currency() == null || event.currency().isBlank()
        || !payload.path("userId").isIntegralNumber()
        || payload.path("userId").asLong() <= 0
        || !CorrelationContext.isValid(envelope.path("correlationId").asText(null))) {
      throw new IllegalArgumentException("PAYMENT_EVENT_ENVELOPE_INVALID");
    }
    try {
      Instant.parse(envelope.path("occurredAt").asText());
      if ("PaymentSucceeded".equals(event.eventType())) {
        Instant.parse(payload.path("paidAt").asText());
        if (payload.path("provider").asText().isBlank()
            || payload.path("providerTransactionId").asText().isBlank()) {
          throw new IllegalArgumentException("PAYMENT_SUCCEEDED_PAYLOAD_INVALID");
        }
      } else if ("PaymentFailed".equals(event.eventType())) {
        Instant.parse(payload.path("failedAt").asText());
        if (event.failureReason() == null || event.failureReason().isBlank()) {
          throw new IllegalArgumentException("PAYMENT_FAILED_PAYLOAD_INVALID");
        }
      }
    } catch (Exception exception) {
      throw new IllegalArgumentException("PAYMENT_EVENT_OCCURRED_AT_INVALID", exception);
    }
  }
}
