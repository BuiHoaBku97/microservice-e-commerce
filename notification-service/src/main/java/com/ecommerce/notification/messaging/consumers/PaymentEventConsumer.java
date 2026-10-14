package com.ecommerce.notification.messaging.consumers;

import com.ecommerce.notification.messaging.events.NotificationEvent;
import com.ecommerce.notification.services.NotificationService;
import com.ecommerce.notification.services.ProcessedEventService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {
  private final ObjectMapper objectMapper;
  private final NotificationService notificationService;
  private final ProcessedEventService processedEventService;

  public PaymentEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
    this(objectMapper, notificationService, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public PaymentEventConsumer(
      ObjectMapper objectMapper,
      NotificationService notificationService,
      ProcessedEventService processedEventService) {
    this.objectMapper = objectMapper;
    this.notificationService = notificationService;
    this.processedEventService = processedEventService;
  }

  @KafkaListener(
      topics = "${app.kafka.topics.payment-events:ecommerce.payment.events}",
      groupId = "${spring.kafka.consumer.group-id:notification-service}")
  public void consume(String message, Acknowledgment acknowledgment) {
    try {
      JsonNode payload = objectMapper.readTree(message);
      Runnable action = () -> notificationService.createFromEvent(NotificationEvent.from(payload));
      if (processedEventService == null) {
        action.run();
      } else {
        processedEventService.process(
            "notification-payment-events",
            payload.path("eventId").asText(),
            payload.path("eventType").asText(),
            action);
      }
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("Không thể xử lý sự kiện thanh toán", exception);
    }
  }
}
