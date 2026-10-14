package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
  Page<StockMovement> findBySkuIdOrderByCreatedAtDesc(Long skuId, Pageable pageable);
}
