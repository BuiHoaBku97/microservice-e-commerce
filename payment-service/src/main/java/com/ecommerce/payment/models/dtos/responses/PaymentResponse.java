package com.ecommerce.payment.models.dtos.responses;

import com.ecommerce.payment.models.entities.Payment;

public record PaymentResponse(
    Long id,
    Long orderId,
    Long userId,
    Long amount,
    String currency,
    String status,
    String providerReference,
    String failureReason) {
  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getOrderId(),
        payment.getUserId(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getStatus().name(),
        payment.getProviderReference(),
        payment.getFailureReason());
  }
}
