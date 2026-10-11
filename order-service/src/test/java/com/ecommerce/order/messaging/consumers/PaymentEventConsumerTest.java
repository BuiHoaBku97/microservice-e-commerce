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
  private static final String VALID_EVENT = "{\"eventType\":\"PaymentCompleted\",\"orderId\":10}";

  @Test
  void shouldAcknowledgeOnlyAfterServiceReturnsSuccessfully() {
    consumer.consume(VALID_EVENT, acknowledgment);
    var order = inOrder(service, acknowledgment);
    order.verify(service).handlePaymentCompleted(10L);
    order.verify(acknowledgment).acknowledge();
  }

  @Test
  void shouldNotAcknowledgeWhenServiceFails() {
    doThrow(new IllegalStateException("Giao dịch thất bại"))
        .when(service).handlePaymentCompleted(10L);
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
            () -> consumer.consume("{\"eventType\":\"PaymentSucceeded\"}", acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service, acknowledgment);
  }

  @Test
  void shouldRejectMissingEventTypeWithoutAcknowledging() {
    assertThatThrownBy(() -> consumer.consume("{}", acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service, acknowledgment);
  }
}
