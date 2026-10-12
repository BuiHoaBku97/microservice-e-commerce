package com.ecommerce.payment.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor
public class OutboxEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String eventId;

  @Column(nullable = false, length = 80)
  private String aggregateType;

  @Column(nullable = false)
  private Long aggregateId;

  @Column(nullable = false, length = 80)
  private String eventType;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(nullable = false)
  private Integer attempts = 0;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  private Instant publishedAt;

  @Column(columnDefinition = "text")
  private String lastError;

  public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
    this(UUID.randomUUID().toString(), aggregateType, aggregateId, eventType, payload);
  }

  public OutboxEvent(
      String eventId, String aggregateType, Long aggregateId, String eventType, String payload) {
    this.eventId = eventId;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.createdAt = Instant.now();
  }

  public void markPublished() {
    status = OutboxStatus.PUBLISHED;
    publishedAt = Instant.now();
    lastError = null;
  }

  public void markFailed(String error) {
    attempts++;
    lastError = error;
  }
}
