package com.ecommerce.payment.services;

import com.ecommerce.payment.exceptions.PaymentNotFoundException;
import com.ecommerce.payment.models.dtos.responses.PaymentResponse;
import com.ecommerce.payment.repositories.PaymentRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentQueryService {
  private final PaymentRepository paymentRepository;

  public PaymentQueryService(PaymentRepository paymentRepository) {
    this.paymentRepository = paymentRepository;
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
}
