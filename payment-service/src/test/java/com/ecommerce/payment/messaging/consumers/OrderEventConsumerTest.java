package com.ecommerce.payment.messaging.consumers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ecommerce.payment.services.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class OrderEventConsumerTest {
  private final PaymentService service = mock(PaymentService.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final OrderEventConsumer consumer = new OrderEventConsumer(new ObjectMapper(), service);
  private static final String VALID_EVENT = "{\"eventId\":\"evt-1\",\"eventType\":\"OrderCreated\",\"occurredAt\":\"2026-10-12T10:00:00Z\",\"aggregateType\":\"Order\",\"aggregateId\":10,\"orderId\":10,\"userId\":1,\"totalAmount\":100000,\"currency\":\"VND\"}";

  @Test
  void shouldAcknowledgeOnlyAfterServiceReturnsSuccessfully() {
    consumer.consume(VALID_EVENT, acknowledgment);
    var order = inOrder(service, acknowledgment);
    order.verify(service).processOrderPayment(any());
    order.verify(acknowledgment).acknowledge();
  }

  @Test
  void shouldNotAcknowledgeWhenServiceFails() {
    doThrow(new IllegalStateException("Giao dịch thất bại"))
        .when(service).processOrderPayment(any());
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
            () -> consumer.consume("{\"eventType\":\"OrderCancelled\"}", acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service, acknowledgment);
  }

  @Test
  void shouldRejectMissingEventTypeWithoutAcknowledging() {
    assertThatThrownBy(() -> consumer.consume("{}", acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service, acknowledgment);
  }

  @Test
  void shouldAcknowledgeKnownUnrelatedEventWithoutChangingBusinessData() {
    consumer.consume(
        "{\"eventId\":\"evt-shipping\",\"eventType\":\"OrderShipping\",\"occurredAt\":\"2026-10-12T10:00:00Z\",\"aggregateType\":\"Order\",\"aggregateId\":10,\"orderId\":10}",
        acknowledgment);
    verify(acknowledgment).acknowledge();
    verifyNoInteractions(service);
  }
}
