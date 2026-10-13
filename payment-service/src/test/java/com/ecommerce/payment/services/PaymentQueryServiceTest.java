package com.ecommerce.payment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecommerce.payment.exceptions.PaymentNotFoundException;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.repositories.PaymentRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentQueryServiceTest {
  private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
  private final PaymentQueryService service = new PaymentQueryService(
      paymentRepository,
      org.mockito.Mockito.mock(
          com.ecommerce.payment.repositories.PaymentStatusHistoryRepository.class));

  @Test
  void returnsOnlyPaymentMatchingOrderAndAuthenticatedUser() {
    when(paymentRepository.findByOrderIdAndUserId(10L, 1L))
        .thenReturn(Optional.of(new Payment(10L, 1L, 50000L, "VND")));

    assertThat(service.findByOrder(1L, 10L).userId()).isEqualTo(1L);
    verify(paymentRepository).findByOrderIdAndUserId(10L, 1L);
  }

  @Test
  void returnsNotFoundWhenPaymentIsNotProcessedYetOrBelongsToAnotherUser() {
    when(paymentRepository.findByOrderIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByOrder(2L, 10L))
        .isInstanceOf(PaymentNotFoundException.class);
    verify(paymentRepository).findByOrderIdAndUserId(10L, 2L);
  }

  @Test
  void rejectsInvalidIdentifiersWithoutQueryingDatabase() {
    assertThatThrownBy(() -> service.findByOrder(1L, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(paymentRepository);
  }
}
