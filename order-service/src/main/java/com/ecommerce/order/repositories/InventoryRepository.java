package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.Inventory;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
  List<Inventory> findBySkuIdIn(List<Long> skuIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Inventory i where i.skuId in :skuIds")
  List<Inventory> findBySkuIdInForUpdate(@Param("skuIds") List<Long> skuIds);
}
