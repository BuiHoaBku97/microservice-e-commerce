package com.ecommerce.payment.services;

import com.ecommerce.payment.exceptions.PaymentNotFoundException;
import com.ecommerce.payment.models.dtos.responses.PaymentResponse;
import com.ecommerce.payment.repositories.PaymentRepository;
import org.springframework.stereotype.Service;
import com.ecommerce.payment.models.entities.PaymentStatus;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ecommerce.payment.repositories.PaymentStatusHistoryRepository;
import com.ecommerce.payment.models.dtos.responses.PaymentStatusHistoryResponse;

@Service
public class PaymentQueryService {
  private final PaymentRepository paymentRepository;
  private final PaymentStatusHistoryRepository historyRepository;

  public PaymentQueryService(
      PaymentRepository paymentRepository, PaymentStatusHistoryRepository historyRepository) {
    this.paymentRepository = paymentRepository;
    this.historyRepository = historyRepository;
  }

  public PaymentResponse findByOrder(Long userId, Long orderId) {
    if (userId == null || userId <= 0 || orderId == null || orderId <= 0) {
      throw new IllegalArgumentException("Mã đơn hàng không hợp lệ");
    }
    return paymentRepository
        .findByOrderIdAndUserId(orderId, userId)
        .map(PaymentResponse::from)
        .orElseThrow(PaymentNotFoundException::new);
  }

  public Page<PaymentResponse> findForAdmin(
      PaymentStatus status, Long orderId, Long userId, Instant from, Instant to, Pageable pageable) {
    return paymentRepository.findForAdmin(status, orderId, userId, from, to, pageable)
        .map(PaymentResponse::from);
  }

  public PaymentResponse findByIdForAdmin(Long id) {
    return paymentRepository.findById(id)
        .map(PaymentResponse::from)
        .orElseThrow(PaymentNotFoundException::new);
  }

  public PaymentReport report() {
    return new PaymentReport(
        paymentRepository.sumReceivedAmount(),
        paymentRepository.sumAcceptedAmount(),
        paymentRepository.sumRequiresReviewAmount());
  }

  public Page<PaymentStatusHistoryResponse> history(Long paymentId, Pageable pageable) {
    if (!paymentRepository.existsById(paymentId)) throw new PaymentNotFoundException();
    return historyRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId, pageable)
        .map(PaymentStatusHistoryResponse::from);
  }

  public record PaymentReport(
      long receivedAmount, long acceptedAmount, long requiresReviewAmount) {}
}
