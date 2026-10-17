package com.ecommerce.order.messaging.consumers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ecommerce.order.services.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class PaymentEventConsumerTest {
  private final OrderService service = mock(OrderService.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final PaymentEventConsumer consumer = new PaymentEventConsumer(new ObjectMapper(), service);
  private static final String VALID_EVENT =
      "{\"eventId\":\"evt-1\",\"eventType\":\"PaymentSucceeded\",\"schemaVersion\":1,"
          + "\"occurredAt\":\"2026-10-12T10:00:00Z\",\"aggregateType\":\"PAYMENT\","
          + "\"aggregateId\":20,\"aggregateVersion\":0,\"correlationId\":\"corr-1\","
          + "\"payload\":{\"paymentId\":20,\"orderId\":10,\"userId\":7,\"amount\":100,"
          + "\"currency\":\"VND\",\"provider\":\"MOCK\","
          + "\"providerTransactionId\":\"tx-1\",\"paidAt\":\"2026-10-12T09:59:00Z\"}}";

  @Test
  void shouldAcknowledgeOnlyAfterServiceReturnsSuccessfully() {
    consumer.consume(VALID_EVENT, acknowledgment);
    var order = inOrder(service, acknowledgment);
    order.verify(service).handlePaymentSucceeded(org.mockito.ArgumentMatchers.any());
    order.verify(acknowledgment).acknowledge();
  }

  @Test
  void shouldNotAcknowledgeWhenServiceFails() {
    doThrow(new IllegalStateException("Giao dịch thất bại"))
        .when(service).handlePaymentSucceeded(org.mockito.ArgumentMatchers.any());
    assertThatThrownBy(() -> consumer.consume(VALID_EVENT, acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(acknowledgment);
  }

  @Test
  void shouldRejectMalformedJsonWithoutAcknowledging() {
    assertThatThrownBy(() -> consumer.consume("{", acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service, acknowledgment);
  }

  @Test
  void shouldRejectUnsupportedEventWithoutAcknowledging() {
    assertThatThrownBy(
            () -> consumer.consume("{\"eventType\":\"PaymentCompleted\"}", acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service, acknowledgment);
  }

  @Test
  void shouldRejectMissingPaymentIdAsPermanentValidationError() {
    assertThatThrownBy(() -> consumer.consume(
        VALID_EVENT.replace("\"paymentId\":20,", ""), acknowledgment))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(service, acknowledgment);
  }

  @Test
  void shouldRejectMissingEventTypeWithoutAcknowledging() {
    assertThatThrownBy(() -> consumer.consume("{}", acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service, acknowledgment);
  }
}
