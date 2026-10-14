package com.ecommerce.payment.messaging.consumers;

import com.ecommerce.payment.messaging.events.OrderCreatedEvent;
import com.ecommerce.payment.services.PaymentService;
import com.ecommerce.payment.services.ProcessedEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  public OrderEventConsumer(
      ObjectMapper objectMapper,
      PaymentService paymentService,
      ProcessedEventService processedEventService) {
    this.objectMapper = objectMapper;
    this.paymentService = paymentService;
    this.processedEventService = processedEventService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.order-events:ecommerce.order.events}",
      groupId = "${spring.kafka.consumer.group-id:payment-service}")
  public void consume(String message, Acknowledgment acknowledgment) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      validateEnvelope(payload);
      Runnable action = () -> handle(payload);
      if (processedEventService == null) {
        action.run();
      } else {
        processedEventService.process(
            "payment-order-events",
            payload.path("eventId").asText(),
            payload.path("eventType").asText(),
            action);
      }
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện đơn hàng", exception);
    }
  }

  private void handle(JsonNode payload) {
    switch (payload.path("eventType").asText()) {
      case "OrderCreated" -> paymentService.processOrderPayment(OrderCreatedEvent.from(payload));
      case "OrderConfirmed" ->
          paymentService.handleOrderConfirmed(
              payload.path("orderId").asLong(), payload.path("paymentId").asLong());
      case "OrderCancelled" ->
          paymentService.handleOrderCancelled(
              payload.path("orderId").asLong(), payload.path("reason").asText());
      case "OrderConfirmationRejected" ->
          paymentService.handleOrderConfirmationRejected(
              payload.path("orderId").asLong(),
              payload.path("paymentId").asLong(),
              payload.path("reason").asText());
      case "OrderShipping", "OrderCompleted" -> {
        // Các mốc giao hàng không thay đổi giao dịch thanh toán.
      }
      default -> throw new IllegalArgumentException("Loại sự kiện đơn hàng chưa được hỗ trợ");
    }
  }

  private void validateEnvelope(JsonNode payload) {
    if (payload.path("eventId").asText().isBlank()
        || payload.path("eventType").asText().isBlank()
        || !"Order".equals(payload.path("aggregateType").asText())
        || payload.path("aggregateId").asLong(0) != payload.path("orderId").asLong(0)
        || !payload.hasNonNull("occurredAt")) {
      throw new IllegalArgumentException("Envelope sự kiện đơn hàng không hợp lệ");
    }
    try {
      java.time.Instant.parse(payload.get("occurredAt").asText());
    } catch (Exception exception) {
      throw new IllegalArgumentException("occurredAt không hợp lệ", exception);
    }
  }
}
