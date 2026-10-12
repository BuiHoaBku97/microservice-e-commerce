package com.ecommerce.payment.messaging.publishers;

import com.ecommerce.payment.models.entities.OutboxEvent;
import com.ecommerce.payment.models.entities.OutboxStatus;
import com.ecommerce.payment.repositories.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String paymentEventsTopic;
  private final ObjectMapper objectMapper;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${app.kafka.topics.payment-events:ecommerce.payment.events}") String paymentEventsTopic,
      ObjectMapper objectMapper) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.paymentEventsTopic = paymentEventsTopic;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelayString = "${app.outbox.publish-delay-ms:5000}")
  public void publishPendingEvents() {
    List<OutboxEvent> pending =
        outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
    pending.forEach(this::publish);
  }

  private void publish(OutboxEvent event) {
    try {
      kafkaTemplate
          .send(paymentEventsTopic, partitionKey(event), event.getPayload())
          .get(10, TimeUnit.SECONDS);
      event.markPublished();
    } catch (Exception exception) {
      event.markFailed(exception.getMessage());
    }
    outboxEventRepository.save(event);
  }

  private String partitionKey(OutboxEvent event) {
    try {
      JsonNode payload = objectMapper.readTree(event.getPayload());
      JsonNode orderId = payload.get("orderId");
      return orderId != null && !orderId.isNull()
          ? orderId.asText()
          : String.valueOf(event.getAggregateId());
    } catch (Exception exception) {
      return String.valueOf(event.getAggregateId());
    }
  }
}
