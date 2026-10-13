package com.ecommerce.notification.messaging.consumers;

import com.ecommerce.notification.messaging.events.NotificationEvent;
import com.ecommerce.notification.services.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class OrderEventConsumer {
  private static final Set<String> SUPPORTED_EVENTS =
      Set.of(
          "OrderCreated",
          "OrderConfirmed",
          "OrderConfirmationRejected",
          "OrderShipping",
          "OrderCompleted",
          "OrderCancelled");
  private final ObjectMapper objectMapper;
  private final NotificationService notificationService;

  public OrderEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
    this.objectMapper = objectMapper;
    this.notificationService = notificationService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.order-events:ecommerce.order.events}",
      groupId = "${spring.kafka.consumer.group-id:notification-service}")
  public void consume(String message, Acknowledgment acknowledgment) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      String eventType = payload.path("eventType").asText();
      if (!SUPPORTED_EVENTS.contains(eventType)) {
        throw new IllegalArgumentException("Loại sự kiện đơn hàng chưa được hỗ trợ");
      }
      notificationService.createFromEvent(NotificationEvent.from(payload));
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện đơn hàng", exception);
    }
  }
}
