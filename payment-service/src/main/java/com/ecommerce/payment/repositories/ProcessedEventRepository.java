package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
  Optional<ProcessedEvent> findByConsumerNameAndEventId(String consumerName, String eventId);

  @Modifying
  @Query(
      value =
          "INSERT INTO processed_events(consumer_name,event_id,event_type,payload_hash,aggregate_id,processed_at) "
              + "VALUES (:consumer,:eventId,:eventType,:payloadHash,:aggregateId,now()) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int claim(
      @Param("consumer") String consumer,
      @Param("eventId") String eventId,
      @Param("eventType") String eventType,
      @Param("payloadHash") String payloadHash,
      @Param("aggregateId") Long aggregateId);
}
