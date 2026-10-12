package com.ecommerce.order.messaging.publishers;

import com.ecommerce.order.models.entities.OutboxEvent;
import com.ecommerce.order.models.entities.OutboxStatus;
import com.ecommerce.order.repositories.OutboxEventRepository;
import java.util.List;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(OutboxPublisher.class);
  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final Counter published;
  private final Counter failed;
  private final String orderEventsTopic;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      KafkaTemplate<String, String> kafkaTemplate,
      MeterRegistry meterRegistry,
      @Value("${app.kafka.topics.order-events:ecommerce.order.events}") String orderEventsTopic) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.published = meterRegistry.counter("outbox.publish", "result", "acknowledged");
    this.failed = meterRegistry.counter("outbox.publish", "result", "failed");
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
      published.increment();
      log.info("outbox outcome=published eventId={} aggregateId={} eventType={}",
          event.getEventId(), event.getAggregateId(), event.getEventType());
    } catch (Exception exception) {
      if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
      event.markFailed(exception.getMessage());
      failed.increment();
      log.warn("outbox outcome=failed eventId={} aggregateId={} eventType={} attempts={}",
          event.getEventId(), event.getAggregateId(), event.getEventType(), event.getAttempts());
    }
    outboxEventRepository.save(event);
  }
}
