package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.Inventory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
  List<Inventory> findBySkuIdIn(List<Long> skuIds);
}
