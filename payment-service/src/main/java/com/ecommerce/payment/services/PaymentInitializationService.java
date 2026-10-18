package com.ecommerce.payment.services;

import com.ecommerce.payment.models.dtos.responses.PaymentInitializationResponse;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.PaymentStatus;
import com.ecommerce.payment.models.entities.PaymentTransaction;
import com.ecommerce.payment.repositories.PaymentRepository;
import com.ecommerce.payment.repositories.PaymentTransactionRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentInitializationService {
  private final PaymentRepository paymentRepository;
  private final PaymentTransactionRepository transactionRepository;
  private final VnpayService vnpayService;

  public PaymentInitializationService(PaymentRepository paymentRepository,
      PaymentTransactionRepository transactionRepository, VnpayService vnpayService) {
    this.paymentRepository = paymentRepository;
    this.transactionRepository = transactionRepository;
    this.vnpayService = vnpayService;
  }

  @Transactional
  public PaymentInitializationResponse initialize(Long userId, Long orderId, String clientIp) {
    Payment payment = paymentRepository.findByOrderIdAndUserIdForUpdate(orderId, userId)
        .orElseThrow(() -> new IllegalArgumentException("PAYMENT_NOT_FOUND"));
    if (payment.getPaymentDueAt().isBefore(Instant.now()))
      throw new IllegalStateException("PAYMENT_EXPIRED");
    if (payment.getStatus() != PaymentStatus.PENDING)
      throw new IllegalStateException("PAYMENT_NOT_INITIALIZABLE");
    if ("MOCK_ONLINE".equals(payment.getPaymentMethod()))
      return new PaymentInitializationResponse(payment.getId(), payment.getOrderId(),
          payment.getPaymentMethod(), "SIMULATE", null);
    if (!"VNPAY".equals(payment.getPaymentMethod()))
      throw new IllegalStateException("PAYMENT_METHOD_UNSUPPORTED");

    PaymentTransaction transaction = transactionRepository
        .findFirstByPaymentIdAndProviderAndStatusOrderByCreatedAtDesc(
            payment.getId(), "VNPAY", PaymentTransaction.Status.CREATED)
        .orElseGet(() -> transactionRepository.save(new PaymentTransaction(
            payment.getId(), "VNPAY", "PAY-" + payment.getId() + "-" + UUID.randomUUID(),
            payment.getAmount(), payment.getCurrency())));
    return new PaymentInitializationResponse(payment.getId(), payment.getOrderId(),
        payment.getPaymentMethod(), "REDIRECT", vnpayService.createUrl(payment, transaction, clientIp));
  }
}
