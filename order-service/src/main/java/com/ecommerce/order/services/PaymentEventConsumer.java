package com.ecommerce.order.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {
  private final ObjectMapper objectMapper;
  private final OrderService orders;

  public PaymentEventConsumer(ObjectMapper objectMapper, OrderService orders) {
    this.objectMapper = objectMapper;
    this.orders = orders;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.payment-events:ecommerce.payment.events}",
      groupId = "${spring.kafka.consumer.group-id:order-service}")
  public void consume(String message) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      PaymentEvent event = PaymentEvent.from(payload);
      if ("PaymentCompleted".equals(event.eventType())) {
        orders.handlePaymentCompleted(event.orderId());
      } else if ("PaymentFailed".equals(event.eventType())) {
        orders.handlePaymentFailed(event.orderId());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện thanh toán", exception);
    }
  }
}
