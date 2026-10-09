package com.ecommerce.payment.services;

import com.ecommerce.payment.models.entities.OutboxEvent;
import com.ecommerce.payment.models.entities.OutboxStatus;
import com.ecommerce.payment.repositories.OutboxEventRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
  private final OutboxEventRepository events;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String paymentEventsTopic;

  public OutboxPublisher(
      OutboxEventRepository events,
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${app.kafka.topics.payment-events:ecommerce.payment.events}")
          String paymentEventsTopic) {
    this.events = events;
    this.kafkaTemplate = kafkaTemplate;
    this.paymentEventsTopic = paymentEventsTopic;
  }

  @Scheduled(fixedDelayString = "${app.outbox.publish-delay-ms:5000}")
  public void publishPendingEvents() {
    List<OutboxEvent> pending = events.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
    pending.forEach(this::publish);
  }

  private void publish(OutboxEvent event) {
    try {
      kafkaTemplate
          .send(paymentEventsTopic, String.valueOf(event.getAggregateId()), event.getPayload())
          .get(10, TimeUnit.SECONDS);
      event.markPublished();
    } catch (Exception exception) {
      event.markFailed(exception.getMessage());
    }
    events.save(event);
  }
}
