package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
  List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

  Optional<Order> findByIdAndUserId(Long id, Long userId);
}
