package com.ecommerce.notification.messaging.consumers;

import com.ecommerce.notification.messaging.events.NotificationEvent;
import com.ecommerce.notification.services.NotificationService;
import com.ecommerce.notification.services.ProcessedEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

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
  private final ProcessedEventService processedEventService;

  public OrderEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
    this(objectMapper, notificationService, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public OrderEventConsumer(
      ObjectMapper objectMapper,
      NotificationService notificationService,
      ProcessedEventService processedEventService) {
    this.objectMapper = objectMapper;
    this.notificationService = notificationService;
    this.processedEventService = processedEventService;
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
      Runnable action = () -> notificationService.createFromEvent(NotificationEvent.from(payload));
      if (processedEventService == null) {
        action.run();
      } else {
        processedEventService.process(
            "notification-order-events", payload.path("eventId").asText(), eventType, action);
      }
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện đơn hàng", exception);
    }
  }
}
