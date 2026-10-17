package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.CatalogOutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CatalogOutboxRepository extends JpaRepository<CatalogOutboxEvent, Long> {
  @Query(
      value =
          "WITH candidates AS (SELECT id FROM catalog_outbox_events "
              + "WHERE (status='PENDING' AND next_attempt_at<=now()) "
              + "OR (status='PROCESSING' AND lease_until<now()) "
              + "ORDER BY occurred_at FOR UPDATE SKIP LOCKED LIMIT 50) "
              + "UPDATE catalog_outbox_events o SET status='PROCESSING', claimed_by=:workerId, "
              + "lease_until=now()+interval '30 seconds' FROM candidates c WHERE o.id=c.id "
              + "RETURNING o.*",
      nativeQuery = true)
  List<CatalogOutboxEvent> claimBatch(@Param("workerId") String workerId);
}
