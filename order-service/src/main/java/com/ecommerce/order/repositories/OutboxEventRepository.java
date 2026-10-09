package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.OutboxEvent;
import com.ecommerce.order.models.entities.OutboxStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
  List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
