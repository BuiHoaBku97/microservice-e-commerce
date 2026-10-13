package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
  List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
