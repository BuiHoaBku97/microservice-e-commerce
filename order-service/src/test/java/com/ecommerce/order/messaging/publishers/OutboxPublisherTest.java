package com.ecommerce.order.messaging.publishers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.ecommerce.order.models.entities.OutboxEvent;
import com.ecommerce.order.models.entities.OutboxStatus;
import com.ecommerce.order.repositories.OutboxEventRepository;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxPublisherTest {
  private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
  private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final OutboxPublisher publisher =
      new OutboxPublisher(repository, kafkaTemplate, meterRegistry, "ecommerce.order.events");

  @Test
  void shouldResendSameIdentity_whenBrokerAckSucceedsButDatabaseSaveFails() {
    String payload = "{\"eventId\":\"crash-event\",\"orderId\":10}";
    OutboxEvent first = new OutboxEvent("crash-event", "Order", 10L,
        "OrderCreated", payload);
    // Simulates loading the durable PENDING row after the previous save failed.
    OutboxEvent reloaded = new OutboxEvent("crash-event", "Order", 10L,
        "OrderCreated", payload);
    when(repository.claimPendingBatch(any(String.class)))
        .thenReturn(List.of(first), List.of(reloaded));
    when(kafkaTemplate.send("ecommerce.order.events", "10", payload))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(repository.save(any(OutboxEvent.class)))
        .thenThrow(new IllegalStateException("database unavailable"))
        .thenReturn(reloaded);

    assertThatThrownBy(publisher::publishPendingEvents).isInstanceOf(IllegalStateException.class);
    publisher.publishPendingEvents();

    verify(kafkaTemplate, times(2)).send("ecommerce.order.events", "10", payload);
    assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(reloaded.getEventId()).isEqualTo(first.getEventId());
  }

  @Test
  void shouldRepublishStoredPayloadWithAggregateIdKeyAndKeepEventId() throws Exception {
    String payload =
        "{\"eventId\":\"order-event-1\",\"eventType\":\"OrderCreated\","
            + "\"orderId\":10}";
    OutboxEvent event = new OutboxEvent("order-event-1", "Order", 10L, "OrderCreated", payload);
    when(repository.claimPendingBatch(any(String.class)))
        .thenReturn(List.of(event));
    when(kafkaTemplate.send(eq("ecommerce.order.events"), eq("10"), eq(payload)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
        .thenReturn(CompletableFuture.completedFuture(null));

    publisher.publishPendingEvents();

    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(event.getAttempts()).isEqualTo(1);
    assertThat(event.getPublishedAt()).isNull();
    publisher.publishPendingEvents();

    verify(kafkaTemplate, times(2)).send("ecommerce.order.events", "10", payload);
    verify(repository, times(2)).save(event);
    assertThat(event.getEventId()).isEqualTo("order-event-1");
    assertThat(event.getPayload()).contains("\"eventId\":\"order-event-1\"");
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(event.getLastError()).isNull();
    assertThat(meterRegistry.get("outbox.publish").tag("result", "failed").counter().count()).isEqualTo(1);
    assertThat(meterRegistry.get("outbox.publish").tag("result", "acknowledged").counter().count()).isEqualTo(1);
  }
}
