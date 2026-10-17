package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.entities.OutboxEvent;
import com.ecommerce.payment.models.entities.OutboxStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
  List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

  @Query(
      value =
          """
          WITH candidates AS (
            SELECT id FROM outbox_events
            WHERE (status='PENDING' AND next_attempt_at <= now())
               OR (status='PROCESSING' AND lease_until < now())
            ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 50
          )
          UPDATE outbox_events o SET status='PROCESSING', claimed_by=:workerId,
            lease_until=now() + interval '30 seconds', updated_at=now()
          FROM candidates c WHERE o.id=c.id RETURNING o.*
          """,
      nativeQuery = true)
  List<OutboxEvent> claimPendingBatch(@Param("workerId") String workerId);

  List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
