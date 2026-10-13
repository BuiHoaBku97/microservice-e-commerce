package com.ecommerce.payment.models.dtos.responses;

import com.ecommerce.payment.models.entities.Payment;
import java.time.Instant;

public record PaymentResponse(
    Long id,
    Long orderId,
    Long userId,
    Long amount,
    String currency,
    String status,
    String fundsStatus,
    String orderAcceptance,
    String reviewReason,
    String providerReference,
    String providerTransactionId,
    String failureReason,
    Instant paidAt,
    Instant paymentDueAt) {
  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getOrderId(),
        payment.getUserId(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getStatus().name(),
        payment.getFundsStatus().name(),
        payment.getOrderAcceptance().name(),
        payment.getReviewReason(),
        payment.getProviderReference(),
        payment.getProviderTransactionId(),
        payment.getFailureReason(),
        payment.getPaidAt(),
        payment.getPaymentDueAt());
  }
}
