package com.ecommerce.payment.messaging.consumers;

import com.ecommerce.payment.messaging.events.OrderCreatedEvent;
import com.ecommerce.payment.services.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {
  private final ObjectMapper objectMapper;
  private final PaymentService paymentService;

  public OrderEventConsumer(ObjectMapper objectMapper, PaymentService paymentService) {
    this.objectMapper = objectMapper;
    this.paymentService = paymentService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.order-events:ecommerce.order.events}",
      groupId = "${spring.kafka.consumer.group-id:payment-service}")
  public void consume(String message, Acknowledgment acknowledgment) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      if ("OrderCreated".equals(payload.path("eventType").asText())) {
        paymentService.processOrderPayment(OrderCreatedEvent.from(payload));
        acknowledgment.acknowledge();
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện đơn hàng", exception);
    }
  }
}
