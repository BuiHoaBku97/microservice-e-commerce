package com.ecommerce.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.PaymentStatus;
import com.ecommerce.payment.messaging.events.OrderCreatedEvent;
import com.ecommerce.payment.repositories.PaymentRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentEventService paymentEventService;

  @InjectMocks private PaymentService service;

  @Test
  void shouldCompleteValidOrderPayment() {
    when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Payment payment =
        service.processOrderPayment(new OrderCreatedEvent(10L, 20L, 100_000L, "VND"));

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.getProviderReference()).isEqualTo("SIMULATED-10");
    verify(paymentEventService).enqueuePaymentResult(payment);
  }

  @Test
  void shouldReturnExistingPaymentWhenOrderEventIsDuplicated() {
    Payment existing = new Payment(10L, 20L, 100_000L, "VND");
    existing.complete("SIMULATED-10");
    when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(existing));

    Payment payment =
        service.processOrderPayment(new OrderCreatedEvent(10L, 20L, 100_000L, "VND"));

    assertThat(payment).isSameAs(existing);
    verify(paymentRepository, never()).save(any(Payment.class));
    verify(paymentEventService, never()).enqueuePaymentResult(any(Payment.class));
  }

  @Test
  void shouldFailPaymentWhenAmountIsInvalid() {
    when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Payment payment = service.processOrderPayment(new OrderCreatedEvent(10L, 20L, 0L, "VND"));

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).isEqualTo("Số tiền thanh toán không hợp lệ");
    verify(paymentEventService).enqueuePaymentResult(payment);
  }
}
