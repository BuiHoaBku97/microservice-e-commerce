package com.ecommerce.order.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ecommerce.order.configs.CorrelationContext;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OutboxEvent;
import com.ecommerce.order.repositories.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxEventServiceTest {
  private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final OutboxEventService service = new OutboxEventService(repository, mapper);

  @AfterEach
  void clearContext() {
    CorrelationContext.clear();
  }

  @Test
  void orderCreatedUsesVersionOneEnvelopeAndExactNestedPayload() throws Exception {
    Order order = order();
    CorrelationContext.set("checkout-correlation");

    service.enqueueOrderCreated(order);

    OutboxEvent event = captured();
    var json = mapper.readTree(event.getPayload());
    assertThat(event.getCorrelationId()).isEqualTo("checkout-correlation");
    assertThat(json.path("correlationId").asText()).isEqualTo("checkout-correlation");
    assertThat(json.path("aggregateType").asText()).isEqualTo("ORDER");
    assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(json.has("orderId")).isFalse();
    assertThat(json.has("items")).isFalse();
    assertThat(json.path("payload").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "orderId", "userId", "totalAmount", "currency", "paymentDueAt", "orderNumber");
    assertThat(json.path("payload").path("orderNumber").asText()).isEqualTo("ORD-000010");
  }

  private Order order() {
    Order order = new Order(7L, 100_000L, "VND", Instant.parse("2026-10-17T10:15:00Z"));
    ReflectionTestUtils.setField(order, "id", 10L);
    ReflectionTestUtils.setField(order, "orderNumber", "ORD-000010");
    return order;
  }

  private OutboxEvent captured() {
    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repository).save(captor.capture());
    return captor.getValue();
  }
}
