package com.ecommerce.notification.messaging.consumers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ecommerce.notification.services.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class OrderEventConsumerTest {
  private final NotificationService service = mock(NotificationService.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final OrderEventConsumer consumer = new OrderEventConsumer(new ObjectMapper(), service);
  private static final String VALID_EVENT = "{\"eventType\":\"OrderCreated\",\"orderId\":10,\"userId\":1}";

  @Test
  void shouldAcknowledgeOnlyAfterServiceReturnsSuccessfully() {
    consumer.consume(VALID_EVENT, acknowledgment);
    var order = inOrder(service, acknowledgment);
    order.verify(service).createFromEvent(any());
    order.verify(acknowledgment).acknowledge();
  }

  @Test
  void shouldNotifyAndAcknowledgeOrderConfirmed() {
    consumer.consume(
        "{\"eventId\":\"order-confirmed-1\",\"eventType\":\"OrderConfirmed\",\"orderId\":10,\"userId\":1,\"status\":\"CONFIRMED\"}",
        acknowledgment);

    verify(service).createFromEvent(any());
    verify(acknowledgment).acknowledge();
  }

  @Test
  void shouldNotAcknowledgeWhenServiceFails() {
    doThrow(new IllegalStateException("Giao dịch thất bại"))
        .when(service).createFromEvent(any());
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
            () -> consumer.consume("{\"eventType\":\"UnknownEvent\"}", acknowledgment))
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
  void shouldNotifyOrderConfirmationRejected() {
    consumer.consume(
        "{\"eventId\":\"rejected-1\",\"eventType\":\"OrderConfirmationRejected\",\"orderId\":10,\"userId\":1}",
        acknowledgment);
    verify(service).createFromEvent(any());
    verify(acknowledgment).acknowledge();
  }

  @Test
  void shouldRejectOrderEventWithMissingIdentityWithoutAcknowledging() {
    assertThatThrownBy(
            () -> consumer.consume("{\"eventType\":\"OrderConfirmed\",\"orderId\":10}", acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service);
    verifyNoInteractions(acknowledgment);
  }
}
