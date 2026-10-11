package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.entities.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByOrderId(Long orderId);

  Optional<Payment> findByOrderIdAndUserId(Long orderId, Long userId);

  boolean existsByOrderId(Long orderId);
}
