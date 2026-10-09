package com.ecommerce.payment.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {
  private final ObjectMapper objectMapper;
  private final PaymentService payments;

  public OrderEventConsumer(ObjectMapper objectMapper, PaymentService payments) {
    this.objectMapper = objectMapper;
    this.payments = payments;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.order-events:ecommerce.order.events}",
      groupId = "${spring.kafka.consumer.group-id:payment-service}")
  public void consume(String message) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      if ("OrderCreated".equals(payload.path("eventType").asText())) {
        payments.processOrderPayment(OrderCreatedEvent.from(payload));
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện đơn hàng", exception);
    }
  }
}
