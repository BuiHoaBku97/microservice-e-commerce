package com.ecommerce.payment.services;

import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.repositories.PaymentRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
  private final PaymentRepository payments;
  private final PaymentEventService events;

  public PaymentService(PaymentRepository payments, PaymentEventService events) {
    this.payments = payments;
    this.events = events;
  }

  @Transactional
  public Payment processOrderPayment(OrderCreatedEvent order) {
    Optional<Payment> existing = payments.findByOrderId(order.orderId());
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

    Payment saved = payments.save(payment);
    events.enqueuePaymentResult(saved);
    return saved;
  }

  @Transactional
  public Payment getByOrderId(Long orderId) {
    return payments
        .findByOrderId(orderId)
        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thanh toán cho đơn hàng"));
  }
}
