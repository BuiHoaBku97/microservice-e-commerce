package com.ecommerce.notification.repositories;

import com.ecommerce.notification.models.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
  @Modifying
  @Query(
      value =
          "INSERT INTO processed_events(consumer_name,event_id,event_type,processed_at)"
              + " VALUES(:c,:e,:t,now()) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int claim(@Param("c") String consumer, @Param("e") String eventId, @Param("t") String eventType);
}
