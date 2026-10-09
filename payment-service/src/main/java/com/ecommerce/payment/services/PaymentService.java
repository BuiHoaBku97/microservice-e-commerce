package com.ecommerce.payment.services;

import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.repositories.PaymentRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final PaymentEventService paymentEventService;

  public PaymentService(
      PaymentRepository paymentRepository, PaymentEventService paymentEventService) {
    this.paymentRepository = paymentRepository;
    this.paymentEventService = paymentEventService;
  }

  @Transactional
  public Payment processOrderPayment(OrderCreatedEvent order) {
    Optional<Payment> existing = paymentRepository.findByOrderId(order.orderId());
    if (existing.isPresent()) {
      return existing.get();
    }

    Payment payment =
        new Payment(order.orderId(), order.userId(), order.totalAmount(), order.currency());
    if (order.totalAmount() == null || order.totalAmount() <= 0) {
      payment.fail("Số tiền thanh toán không hợp lệ");
    } else {
      payment.complete("SIMULATED-" + order.orderId());
    }

    Payment saved = paymentRepository.save(payment);
    paymentEventService.enqueuePaymentResult(saved);
    return saved;
  }

  @Transactional
  public Payment getByOrderId(Long orderId) {
    return paymentRepository
        .findByOrderId(orderId)
        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thanh toán cho đơn hàng"));
  }
}
