package com.ecommerce.notification.messaging.consumers;

import com.ecommerce.notification.messaging.events.NotificationEvent;
import com.ecommerce.notification.services.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {
  private final ObjectMapper objectMapper;
  private final NotificationService notificationService;

  public OrderEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
    this.objectMapper = objectMapper;
    this.notificationService = notificationService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.order-events:ecommerce.order.events}",
      groupId = "${spring.kafka.consumer.group-id:notification-service}")
  public void consume(String message) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      notificationService.createFromEvent(NotificationEvent.from(payload));
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện đơn hàng", exception);
    }
  }
}
