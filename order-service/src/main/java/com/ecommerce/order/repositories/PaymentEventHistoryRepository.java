package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.PaymentEventHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventHistoryRepository extends JpaRepository<PaymentEventHistory, Long> {
  boolean existsByEventId(String eventId);
}
