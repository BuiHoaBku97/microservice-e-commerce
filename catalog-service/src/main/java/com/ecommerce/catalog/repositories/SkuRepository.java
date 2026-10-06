package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.CatalogStatus;
import com.ecommerce.catalog.models.entities.Sku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkuRepository extends JpaRepository<Sku, Long> {
    boolean existsByCodeIgnoreCase(String code);
    List<Sku> findByProductIdOrderByIdAsc(Long productId);
    List<Sku> findByProductIdAndStatusOrderByIdAsc(Long productId, CatalogStatus status);
}
