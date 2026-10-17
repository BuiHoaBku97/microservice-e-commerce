package com.ecommerce.payment.messaging.consumers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ecommerce.payment.services.PaymentService;
import com.ecommerce.payment.messaging.events.OrderCancelledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.support.Acknowledgment;

class OrderEventConsumerTest {
  private final PaymentService service = mock(PaymentService.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final OrderEventConsumer consumer = new OrderEventConsumer(new ObjectMapper(), service);
  private static final String VALID_EVENT =
      "{\"eventId\":\"evt-1\",\"eventType\":\"OrderCreated\",\"schemaVersion\":1,"
          + "\"occurredAt\":\"2026-10-12T10:00:00Z\",\"aggregateType\":\"ORDER\","
          + "\"aggregateId\":10,\"aggregateVersion\":1,\"correlationId\":\"corr-1\","
          + "\"payload\":{\"orderId\":10,\"userId\":1,\"totalAmount\":100000,"
          + "\"currency\":\"VND\",\"paymentDueAt\":\"2026-10-12T10:15:00Z\","
          + "\"orderNumber\":\"ORD-000010\"}}";

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
  void shouldUpdatePaymentWhenOrderIsConfirmed() {
    consumer.consume(VALID_EVENT.replace("OrderCreated", "OrderConfirmed")
        .replace("\"orderId\":10", "\"orderId\":10,\"paymentId\":20,"
            + "\"confirmedAt\":\"2026-10-12T10:00:00Z\""), acknowledgment);
    verify(service).handleOrderConfirmed(10L, 20L);
    verify(acknowledgment).acknowledge();
  }

  @Test
  void shouldUpdatePaymentWhenOrderIsCancelled() {
    consumer.consume(VALID_EVENT.replace("OrderCreated", "OrderCancelled")
        .replace("\"orderId\":10", "\"orderId\":10,\"reason\":\"PAYMENT_EXPIRED\","
            + "\"cancelledAt\":\"2026-10-12T10:15:00Z\""), acknowledgment);
    verify(service).handleOrderCancelled(any(OrderCancelledEvent.class));
    verify(acknowledgment).acknowledge();
  }

  @Test
  void shouldNotAcknowledgeInvalidCancellationReason() {
    String event =
        VALID_EVENT.replace("OrderCreated", "OrderCancelled")
            .replace("\"orderId\":10", "\"orderId\":10,\"reason\":\"UNKNOWN\","
                + "\"cancelledAt\":\"2026-10-12T10:15:00Z\"");

    assertThatThrownBy(() -> consumer.consume(event, acknowledgment))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(acknowledgment);
  }

  @ParameterizedTest
  @ValueSource(strings = {"OrderShipping", "OrderCompleted"})
  void shouldAcknowledgeKnownUnrelatedEventWithoutChangingBusinessData(String eventType) {
    String lifecycleField =
        "OrderShipping".equals(eventType) ? "shippingAt" : "completedAt";
    consumer.consume(
        VALID_EVENT.replace("OrderCreated", eventType)
            .replace("\"orderId\":10", "\"orderId\":10,\"" + lifecycleField
                + "\":\"2026-10-12T10:00:00Z\""),
        acknowledgment);
    verify(acknowledgment).acknowledge();
    verifyNoInteractions(service);
  }
}
