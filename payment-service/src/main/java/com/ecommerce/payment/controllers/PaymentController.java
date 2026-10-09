package com.ecommerce.payment.controllers;

import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.services.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
  private final PaymentService payments;

  public PaymentController(PaymentService payments) {
    this.payments = payments;
  }

  @GetMapping("/orders/{orderId}")
  public PaymentResponse byOrder(@PathVariable Long orderId) {
    return PaymentResponse.from(payments.getByOrderId(orderId));
  }

  public record PaymentResponse(
      Long id,
      Long orderId,
      Long userId,
      Long amount,
      String currency,
      String status,
      String providerReference,
      String failureReason) {
    static PaymentResponse from(Payment payment) {
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
}
