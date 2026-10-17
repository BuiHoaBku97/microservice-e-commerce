package com.ecommerce.payment.messaging.publishers;

import com.ecommerce.payment.models.entities.OutboxEvent;
import com.ecommerce.payment.models.entities.OutboxStatus;
import com.ecommerce.payment.configs.CorrelationContext;
import com.ecommerce.payment.repositories.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(OutboxPublisher.class);
  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final Counter published;
  private final Counter failed;
  private final String paymentEventsTopic;
  private final ObjectMapper objectMapper;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      KafkaTemplate<String, String> kafkaTemplate,
      MeterRegistry meterRegistry,
      @Value("${app.kafka.topics.payment-events:ecommerce.payment.events}") String paymentEventsTopic,
      ObjectMapper objectMapper) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.published = meterRegistry.counter("outbox.publish", "result", "acknowledged");
    this.failed = meterRegistry.counter("outbox.publish", "result", "failed");
    this.paymentEventsTopic = paymentEventsTopic;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelayString = "${app.outbox.publish-delay-ms:5000}")
  @Transactional
  public void publishPendingEvents() {
    List<OutboxEvent> pending = outboxEventRepository.claimPendingBatch();
    pending.forEach(this::publish);
  }

  private void publish(OutboxEvent event) {
    CorrelationContext.set(event.getCorrelationId());
    try {
      kafkaTemplate
          .send(paymentEventsTopic, partitionKey(event), event.getPayload())
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
    } finally {
      CorrelationContext.clear();
    }
    outboxEventRepository.save(event);
  }

  private String partitionKey(OutboxEvent event) {
    try {
      JsonNode payload = objectMapper.readTree(event.getPayload());
      JsonNode orderId = payload.path("payload").get("orderId");
      if (orderId == null || !orderId.isIntegralNumber() || orderId.asLong() <= 0) {
        throw new IllegalArgumentException("PAYMENT_EVENT_ORDER_ID_INVALID");
      }
      return orderId.asText();
    } catch (Exception exception) {
      throw new IllegalStateException("PAYMENT_EVENT_PARTITION_KEY_INVALID", exception);
    }
  }
}
