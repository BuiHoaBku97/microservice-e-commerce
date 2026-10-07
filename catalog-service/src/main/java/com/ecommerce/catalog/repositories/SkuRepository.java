package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.Sku;
import com.ecommerce.catalog.models.entities.Status;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkuRepository extends JpaRepository<Sku, Long> {
  boolean existsByCodeIgnoreCase(String code);

  List<Sku> findByProductIdOrderByIdAsc(Long productId);

  List<Sku> findByProductIdAndStatusOrderByIdAsc(Long productId, Status status);
}
