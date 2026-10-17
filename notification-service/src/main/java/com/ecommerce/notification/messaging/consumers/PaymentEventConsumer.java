package com.ecommerce.notification.messaging.consumers;

import com.ecommerce.notification.configs.CorrelationContext;
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
  public PaymentEventConsumer(ObjectMapper objectMapper, NotificationService notificationService,
      ProcessedEventService processedEventService) {
    this.objectMapper = objectMapper;
    this.notificationService = notificationService;
    this.processedEventService = processedEventService;
  }

  @KafkaListener(topics = "${app.kafka.topics.payment-events:ecommerce.payment.events}",
      groupId = "${spring.kafka.consumer.group-id:notification-service}")
  public void consume(String message, Acknowledgment acknowledgment) {
    try {
      JsonNode envelope = objectMapper.readTree(message);
      OrderEventConsumer.validate(envelope, "PAYMENT");
      CorrelationContext.set(envelope.get("correlationId").asText());
      Runnable action = () -> notificationService.createFromEvent(NotificationEvent.from(envelope));
      if (processedEventService == null) action.run();
      else processedEventService.process("notification-payment-events",
          envelope.get("eventId").asText(), envelope.get("eventType").asText(),
          ProcessedEventService.payloadHash(message), envelope.get("aggregateId").asLong(), action);
      acknowledgment.acknowledge();
    } catch (Exception exception) {
      throw new IllegalStateException("PAYMENT_EVENT_PROCESSING_FAILED", exception);
    } finally {
      CorrelationContext.clear();
    }
  }
}
