package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.entities.PaymentStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {
  Page<PaymentStatusHistory> findByPaymentIdOrderByCreatedAtDesc(Long paymentId, Pageable pageable);
}
