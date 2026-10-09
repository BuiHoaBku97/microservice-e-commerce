package com.ecommerce.order.services;

import com.ecommerce.order.models.entities.OutboxEvent;
import com.ecommerce.order.models.entities.OutboxStatus;
import com.ecommerce.order.repositories.OutboxEventRepository;
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
  private final String orderEventsTopic;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${app.kafka.topics.order-events:ecommerce.order.events}") String orderEventsTopic) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.orderEventsTopic = orderEventsTopic;
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
          .send(orderEventsTopic, String.valueOf(event.getAggregateId()), event.getPayload())
          .get(10, TimeUnit.SECONDS);
      event.markPublished();
    } catch (Exception exception) {
      event.markFailed(exception.getMessage());
    }
    outboxEventRepository.save(event);
  }
}
