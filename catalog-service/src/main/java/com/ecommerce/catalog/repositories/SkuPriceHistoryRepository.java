package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.SkuPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SkuPriceHistoryRepository extends JpaRepository<SkuPriceHistory, Long> {
  Page<SkuPriceHistory> findBySkuIdOrderByCreatedAtDesc(Long skuId, Pageable pageable);
}
