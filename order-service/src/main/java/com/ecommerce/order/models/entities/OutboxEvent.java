package com.ecommerce.order.models.entities;

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
  private Instant claimedUntil;
  private Instant nextAttemptAt;
  private Instant updatedAt;

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
    this.updatedAt = this.createdAt;
    this.nextAttemptAt = this.createdAt;
  }

  public void markPublished() {
    status = OutboxStatus.PUBLISHED;
    publishedAt = Instant.now();
    lastError = null;
    claimedUntil = null;
    updatedAt = Instant.now();
  }

  public void markFailed(String error) {
    attempts++;
    lastError = error;
    claimedUntil = null;
    updatedAt = Instant.now();
    if (attempts >= 8) {
      status = OutboxStatus.FAILED;
    } else {
      status = OutboxStatus.PENDING;
      nextAttemptAt = Instant.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
    }
  }

  public void replay() {
    if (status != OutboxStatus.FAILED) {
      throw new IllegalStateException("OUTBOX_NOT_FAILED");
    }
    status = OutboxStatus.PENDING;
    attempts = 0;
    lastError = null;
    claimedUntil = null;
    nextAttemptAt = Instant.now();
    updatedAt = Instant.now();
  }
}
