package com.ecommerce.payment.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor
public class ProcessedEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String consumerName;
  private String eventId;
  private String eventType;
  private String payloadHash;
  private Long aggregateId;
  private Instant processedAt;
}
