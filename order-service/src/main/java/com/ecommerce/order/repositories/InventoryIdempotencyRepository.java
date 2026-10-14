package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.InventoryIdempotencyRecord;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InventoryIdempotencyRepository
    extends JpaRepository<InventoryIdempotencyRecord, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from InventoryIdempotencyRecord r where r.idempotencyKey = :key")
  Optional<InventoryIdempotencyRecord> findByKeyForUpdate(@Param("key") String key);
}
