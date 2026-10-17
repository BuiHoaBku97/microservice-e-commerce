package com.ecommerce.notification.repositories;

import com.ecommerce.notification.models.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
  Optional<ProcessedEvent> findByConsumerNameAndEventId(String consumerName, String eventId);

  @Modifying
  @Query(
      value =
          "INSERT INTO processed_events(consumer_name,event_id,event_type,payload_hash,aggregate_id,processed_at)"
              + " VALUES(:c,:e,:t,:h,:a,now()) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int claim(
      @Param("c") String consumer,
      @Param("e") String eventId,
      @Param("t") String eventType,
      @Param("h") String payloadHash,
      @Param("a") Long aggregateId);
}
