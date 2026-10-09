package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.entities.OutboxEvent;
import com.ecommerce.payment.models.entities.OutboxStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
  List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
