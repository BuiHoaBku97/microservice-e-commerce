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
import com.ecommerce.payment.models.dtos.requests.PaymentCallbackRequest;
import com.ecommerce.payment.messaging.events.OrderCreatedEvent;
import com.ecommerce.payment.repositories.PaymentRepository;
import com.ecommerce.payment.repositories.PaymentCallbackRepository;
import com.ecommerce.payment.repositories.PaymentStatusHistoryRepository;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
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
  private PaymentService service;

  @BeforeEach
  void setUp() {
    service = new PaymentService(
        paymentRepository, paymentEventService, callbackRepository, historyRepository,
        "test-secret", 300);
  }

  @Test
  void shouldCreatePendingPaymentForValidOrder() {
    when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());
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
    when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(existing));

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

  private String sign(String timestamp, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(
        mac.doFinal((timestamp + body).getBytes(StandardCharsets.UTF_8)));
  }
}
