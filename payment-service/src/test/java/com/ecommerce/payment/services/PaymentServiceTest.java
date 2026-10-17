package com.ecommerce.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.PaymentStatus;
import com.ecommerce.payment.models.entities.FundsStatus;
import com.ecommerce.payment.models.entities.OrderAcceptance;
import com.ecommerce.payment.models.entities.PaymentStatusHistory;
import com.ecommerce.payment.models.dtos.requests.PaymentCallbackRequest;
import com.ecommerce.payment.messaging.events.OrderCreatedEvent;
import com.ecommerce.payment.messaging.events.OrderCancelledEvent;
import com.ecommerce.payment.models.entities.OrderPaymentContext;
import com.ecommerce.payment.repositories.PaymentRepository;
import com.ecommerce.payment.repositories.PaymentCallbackRepository;
import com.ecommerce.payment.repositories.PaymentStatusHistoryRepository;
import com.ecommerce.payment.repositories.OrderPaymentContextRepository;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentEventService paymentEventService;

  @Mock private PaymentCallbackRepository callbackRepository;
  @Mock private PaymentStatusHistoryRepository historyRepository;
  @Mock private OrderPaymentContextRepository orderPaymentContextRepository;
  private PaymentService service;

  @BeforeEach
  void setUp() {
    service = new PaymentService(
        paymentRepository, paymentEventService, callbackRepository, historyRepository,
        orderPaymentContextRepository, "test-secret", 300);
  }

  @Test
  void shouldCreatePendingPaymentForValidOrder() {
    givenContext(new OrderPaymentContext(10L));
    when(paymentRepository.findByOrderIdForUpdate(10L)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Payment payment =
        service.processOrderPayment(new OrderCreatedEvent(
            10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(300)));

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(payment.getProviderReference()).isNull();
    verify(paymentEventService, never()).enqueuePaymentResult(payment);
  }

  @Test
  void shouldReturnExistingPaymentWhenOrderEventIsDuplicated() {
    Payment existing = new Payment(10L, 20L, 100_000L, "VND");
    givenContext(new OrderPaymentContext(10L));
    when(paymentRepository.findByOrderIdForUpdate(10L)).thenReturn(Optional.of(existing));

    Payment payment =
        service.processOrderPayment(new OrderCreatedEvent(
            10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(300)));

    assertThat(payment).isSameAs(existing);
    verify(paymentRepository, never()).save(any(Payment.class));
    verify(paymentEventService, never()).enqueuePaymentResult(any(Payment.class));
  }

  @Test
  void shouldRejectOrderEventWhenAmountIsInvalid() {
    assertThatThrownBy(() -> service.processOrderPayment(
        new OrderCreatedEvent(10L, 20L, 0L, "VND", Instant.now().plusSeconds(300))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ORDER_CREATED_INVALID");

    verify(paymentRepository, never()).save(any(Payment.class));
  }

  @Test
  void callback_shouldAcceptSignedSuccessAndEmitOneResult() throws Exception {
    Payment payment = new Payment(10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(60));
    ReflectionTestUtils.setField(payment, "id", 30L);
    PaymentCallbackRequest callback = new PaymentCallbackRequest(
        "provider-event-1", "transaction-1", 30L, 10L,
        PaymentCallbackRequest.Result.SUCCESS, 100_000L, "VND", Instant.now());
    String rawBody = "{\"providerEventId\":\"provider-event-1\"}";
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    when(callbackRepository.findByProviderEventId("provider-event-1")).thenReturn(Optional.empty());
    when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(payment)).thenReturn(payment);

    Payment result = service.callback(callback, rawBody, timestamp, sign(timestamp, rawBody));

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(result.getFundsStatus()).isEqualTo(FundsStatus.RECEIVED);
    assertThat(result.getProviderTransactionId()).isEqualTo("transaction-1");
    verify(paymentEventService).enqueuePaymentResult(payment);
    verify(callbackRepository).save(any());
  }

  @Test
  void callback_shouldRejectWrongSignatureBeforeReadingPayment() {
    PaymentCallbackRequest callback = new PaymentCallbackRequest(
        "provider-event-1", "transaction-1", 30L, 10L,
        PaymentCallbackRequest.Result.SUCCESS, 100_000L, "VND", Instant.now());

    assertThatThrownBy(() -> service.callback(
        callback, "{}", Long.toString(Instant.now().getEpochSecond()), "00"))
        .isInstanceOf(SecurityException.class)
        .hasMessage("CALLBACK_UNAUTHENTICATED");
    verify(paymentRepository, never()).findByIdForUpdate(any());
  }

  @Test
  void callback_shouldNotEmitAgainWhenFundsWereAlreadyReceived() throws Exception {
    Payment payment = new Payment(10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(60));
    ReflectionTestUtils.setField(payment, "id", 30L);
    payment.receive("first-transaction", Instant.now());
    PaymentCallbackRequest callback = new PaymentCallbackRequest(
        "provider-event-2", "second-transaction", 30L, 10L,
        PaymentCallbackRequest.Result.SUCCESS, 100_000L, "VND", Instant.now());
    String rawBody = "{\"providerEventId\":\"provider-event-2\"}";
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    when(callbackRepository.findByProviderEventId("provider-event-2")).thenReturn(Optional.empty());
    when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(payment)).thenReturn(payment);

    service.callback(callback, rawBody, timestamp, sign(timestamp, rawBody));

    verify(paymentEventService, times(0)).enqueuePaymentResult(any());
  }

  @Test
  void callback_shouldRequireReviewWhenSuccessArrivesAfterFailure() throws Exception {
    Payment payment = new Payment(10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(60));
    ReflectionTestUtils.setField(payment, "id", 30L);
    payment.fail("PROVIDER_FAILED");
    PaymentCallbackRequest callback = new PaymentCallbackRequest(
        "provider-event-after-failure", "transaction-after-failure", 30L, 10L,
        PaymentCallbackRequest.Result.SUCCESS, 100_000L, "VND", Instant.now());
    String rawBody = "{\"providerEventId\":\"provider-event-after-failure\"}";
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    when(callbackRepository.findByProviderEventId("provider-event-after-failure"))
        .thenReturn(Optional.empty());
    when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(payment)).thenReturn(payment);

    Payment result = service.callback(callback, rawBody, timestamp, sign(timestamp, rawBody));

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.REQUIRES_REVIEW);
    assertThat(result.getFundsStatus()).isEqualTo(FundsStatus.RECEIVED);
    verify(paymentEventService).enqueuePaymentResult(payment);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PAYMENT_EXPIRED", "CUSTOMER_CANCELLED"})
  void receive_shouldRequireReviewWhenFundsArriveAfterOrderClosed(String reason) {
    Payment payment = new Payment(10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(60));
    payment.cancelFromOrder(reason);

    boolean received = payment.receive("late-transaction", Instant.now());

    assertThat(received).isTrue();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUIRES_REVIEW);
    assertThat(payment.getFundsStatus()).isEqualTo(FundsStatus.RECEIVED);
  }

  @ParameterizedTest
  @CsvSource({
    "PAYMENT_FAILED,FAILED",
    "PAYMENT_EXPIRED,EXPIRED",
    "CUSTOMER_CANCELLED,CANCELLED"
  })
  void handleOrderCancelled_shouldMapReasonToTerminalState(
      String reason, PaymentStatus expectedStatus) {
    Payment payment = new Payment(10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(60));
    givenContext(new OrderPaymentContext(10L));
    when(paymentRepository.findByOrderIdForUpdate(10L)).thenReturn(Optional.of(payment));

    service.handleOrderCancelled(cancelEvent(reason));

    assertThat(payment.getStatus()).isEqualTo(expectedStatus);
    assertThat(payment.getOrderAcceptance()).isEqualTo(OrderAcceptance.REJECTED);
    verify(historyRepository).save(any(PaymentStatusHistory.class));
    verify(paymentEventService, never()).enqueuePaymentResult(any());
  }

  @Test
  void handleOrderCancelled_shouldKeepReceivedFundsInReview() {
    Payment payment = new Payment(10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(60));
    payment.receive("received-transaction", Instant.now());
    givenContext(new OrderPaymentContext(10L));
    when(paymentRepository.findByOrderIdForUpdate(10L)).thenReturn(Optional.of(payment));

    service.handleOrderCancelled(cancelEvent("PAYMENT_FAILED"));

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUIRES_REVIEW);
    assertThat(payment.getFundsStatus()).isEqualTo(FundsStatus.RECEIVED);
    assertThat(payment.getOrderAcceptance()).isEqualTo(OrderAcceptance.REJECTED);
    assertThat(payment.getReviewReason()).isEqualTo("PAYMENT_FAILED");
  }

  @Test
  void handleOrderCancelled_shouldRejectUnknownReasonWithoutMutation() {
    Payment payment = new Payment(10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(60));

    assertThatThrownBy(
            () ->
                service.handleOrderCancelled(
                    new OrderCancelledEvent(
                        "cancel-1", 10L, "UNKNOWN", Instant.now(), 1L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ORDER_CANCELLED_INVALID");

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(payment.getOrderAcceptance()).isEqualTo(OrderAcceptance.PENDING);
    verify(historyRepository, never()).save(any());
  }

  @Test
  void orderCancelledBeforeOrderCreated_shouldMaterializeClosedPayment() {
    OrderPaymentContext context = new OrderPaymentContext(10L);
    givenContext(context);
    when(paymentRepository.findByOrderIdForUpdate(10L)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.handleOrderCancelled(cancelEvent("CUSTOMER_CANCELLED"));
    Payment payment =
        service.processOrderPayment(
            new OrderCreatedEvent(
                10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(300)));

    assertThat(context.isClosed()).isTrue();
    assertThat(context.getCancellationReason()).isEqualTo("CUSTOMER_CANCELLED");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    assertThat(payment.getFundsStatus()).isEqualTo(FundsStatus.NOT_RECEIVED);
    assertThat(payment.getOrderAcceptance()).isEqualTo(OrderAcceptance.REJECTED);
    verify(paymentEventService, never()).enqueuePaymentResult(any());
  }

  @Test
  void conflictingCancellationContext_shouldBeRejected() {
    OrderPaymentContext context = new OrderPaymentContext(10L);
    givenContext(context);
    when(paymentRepository.findByOrderIdForUpdate(10L)).thenReturn(Optional.empty());
    service.handleOrderCancelled(cancelEvent("CUSTOMER_CANCELLED"));

    assertThatThrownBy(
            () ->
                service.handleOrderCancelled(
                    new OrderCancelledEvent(
                        "cancel-2", 10L, "PAYMENT_EXPIRED", Instant.now(), 2L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ORDER_CANCELLATION_CONTEXT_CONFLICT");
    assertThat(context.getCancellationReason()).isEqualTo("CUSTOMER_CANCELLED");
  }

  private void givenContext(OrderPaymentContext context) {
    when(orderPaymentContextRepository.findByOrderIdForUpdate(context.getOrderId()))
        .thenReturn(Optional.of(context));
  }

  private OrderCancelledEvent cancelEvent(String reason) {
    return new OrderCancelledEvent("cancel-1", 10L, reason, Instant.now(), 1L);
  }

  private String sign(String timestamp, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(
        mac.doFinal((timestamp + body).getBytes(StandardCharsets.UTF_8)));
  }
}
