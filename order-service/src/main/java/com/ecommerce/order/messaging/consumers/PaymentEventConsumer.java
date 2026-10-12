package com.ecommerce.order.messaging.consumers;

import com.ecommerce.order.messaging.events.PaymentEvent;
import com.ecommerce.order.services.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {
  private final ObjectMapper objectMapper;
  private final OrderService orderService;

  public PaymentEventConsumer(ObjectMapper objectMapper, OrderService orderService) {
    this.objectMapper = objectMapper;
    this.orderService = orderService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.payment-events:ecommerce.payment.events}",
      groupId = "${spring.kafka.consumer.group-id:order-service}")
  public void consume(String message, Acknowledgment acknowledgment) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      PaymentEvent event = PaymentEvent.from(payload);
      validateEnvelope(payload, event);
      if ("PaymentSucceeded".equals(event.eventType())) {
        orderService.handlePaymentSucceeded(event);
      } else if ("PaymentFailed".equals(event.eventType())) {
        orderService.handlePaymentFailed(event);
      } else {
        throw new IllegalArgumentException("Loại sự kiện thanh toán chưa được hỗ trợ");
      }
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện thanh toán", exception);
    }
  }

  private void validateEnvelope(JsonNode payload, PaymentEvent event) {
    if (event.eventId() == null
        || event.eventId().isBlank()
        || event.eventType() == null
        || event.eventType().isBlank()
        || !"Payment".equals(payload.path("aggregateType").asText())
        || payload.path("aggregateId").asLong(0) != event.paymentId()
        || !payload.hasNonNull("occurredAt")) {
      throw new IllegalArgumentException("Envelope sự kiện thanh toán không hợp lệ");
    }
    try {
      java.time.Instant.parse(payload.get("occurredAt").asText());
    } catch (Exception exception) {
      throw new IllegalArgumentException("occurredAt không hợp lệ", exception);
    }
  }
}
