package com.ecommerce.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ecommerce.payment.configs.CorrelationContext;
import com.ecommerce.payment.models.entities.OutboxEvent;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.repositories.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentEventServiceTest {
  private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final PaymentEventService service = new PaymentEventService(repository, mapper);

  @AfterEach
  void clearContext() {
    CorrelationContext.clear();
  }

  @Test
  void paymentSucceededUsesExactNestedPayloadAndStoredPaidAt() throws Exception {
    Instant paidAt = Instant.parse("2026-10-17T10:00:00Z");
    Payment payment = payment(20L);
    payment.receive("provider-transaction", paidAt);
    CorrelationContext.set("callback-correlation");

    service.enqueuePaymentResult(payment);

    var json = mapper.readTree(captured().getPayload());
    assertThat(json.path("aggregateType").asText()).isEqualTo("PAYMENT");
    assertThat(json.has("paymentId")).isFalse();
    assertThat(json.path("payload").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("paymentId", "orderId", "userId", "amount", "currency",
            "provider", "providerTransactionId", "paidAt");
    assertThat(json.path("payload").path("paidAt").asText()).isEqualTo(paidAt.toString());
  }

  @Test
  void paymentFailedUsesReasonAndFailedAtWithoutSuccessFields() throws Exception {
    Payment payment = payment(21L);
    payment.fail("provider_declined");
    CorrelationContext.set("failure-correlation");

    service.enqueuePaymentResult(payment);

    var payload = mapper.readTree(captured().getPayload()).path("payload");
    assertThat(payload.hasNonNull("failedAt")).isTrue();
    assertThat(payload.path("reason").asText()).isEqualTo("provider_declined");
    assertThat(payload.has("paidAt")).isFalse();
    assertThat(payload.has("failureReason")).isFalse();
  }

  private Payment payment(Long id) {
    Payment payment = new Payment(10L, 7L, 100_000L, "VND", Instant.parse("2026-10-17T10:15:00Z"));
    ReflectionTestUtils.setField(payment, "id", id);
    return payment;
  }

  private OutboxEvent captured() {
    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(repository).save(captor.capture());
    return captor.getValue();
  }
}
