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
  private static final String VALID_EVENT =
      "{\"eventId\":\"order-1\",\"eventType\":\"OrderCreated\",\"schemaVersion\":1,"
          + "\"aggregateType\":\"ORDER\",\"aggregateId\":10,\"aggregateVersion\":0,"
          + "\"occurredAt\":\"2026-10-17T10:00:00Z\",\"correlationId\":\"corr-1\","
          + "\"payload\":{\"orderId\":10,\"userId\":1}}";

  @Test
  void shouldAcknowledgeOnlyAfterServiceReturnsSuccessfully() {
    consumer.consume(VALID_EVENT, acknowledgment);
    var order = inOrder(service, acknowledgment);
    order.verify(service).createFromEvent(any());
    order.verify(acknowledgment).acknowledge();
  }

  @Test
  void shouldNotifyAndAcknowledgeOrderConfirmed() {
    consumer.consume(VALID_EVENT.replace("OrderCreated", "OrderConfirmed"), acknowledgment);

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
  void shouldRejectOrderConfirmationRejectedBecauseItHasNoNotificationPayload() {
    assertThatThrownBy(
            () ->
                consumer.consume(
                    VALID_EVENT.replace("OrderCreated", "OrderConfirmationRejected"),
                    acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service, acknowledgment);
  }

  @Test
  void shouldRejectOrderEventWithMissingIdentityWithoutAcknowledging() {
    assertThatThrownBy(
            () -> consumer.consume(VALID_EVENT.replace("\"userId\":1", "\"missing\":1"), acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(service);
    verifyNoInteractions(acknowledgment);
  }
}
