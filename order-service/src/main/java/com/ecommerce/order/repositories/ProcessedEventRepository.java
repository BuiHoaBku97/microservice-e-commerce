package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
  @Modifying
  @Query(
      value =
          "INSERT INTO processed_events(consumer_name,event_id,event_type,processed_at) "
              + "VALUES (:consumer,:eventId,:eventType,now()) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int claim(
      @Param("consumer") String consumer,
      @Param("eventId") String eventId,
      @Param("eventType") String eventType);
}
