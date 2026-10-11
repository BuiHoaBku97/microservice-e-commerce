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
      switch (payload.path("eventType").asText()) {
        case "OrderCreated" ->
            paymentService.processOrderPayment(OrderCreatedEvent.from(payload));
        case "OrderShipping", "OrderCompleted" -> {
          // Các mốc giao hàng không thay đổi giao dịch thanh toán.
        }
        default -> throw new IllegalArgumentException("Loại sự kiện đơn hàng chưa được hỗ trợ");
      }
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện đơn hàng", exception);
    }
  }
}
