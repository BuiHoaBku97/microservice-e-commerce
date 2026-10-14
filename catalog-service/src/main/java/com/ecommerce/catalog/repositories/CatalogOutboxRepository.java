package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.CatalogOutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface CatalogOutboxRepository extends JpaRepository<CatalogOutboxEvent, Long> {
  @Query(
      value =
          "SELECT * FROM catalog_outbox_events WHERE status='PENDING' AND next_attempt_at<=now() "
              + "ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 50",
      nativeQuery = true)
  List<CatalogOutboxEvent> claimBatch();
}
