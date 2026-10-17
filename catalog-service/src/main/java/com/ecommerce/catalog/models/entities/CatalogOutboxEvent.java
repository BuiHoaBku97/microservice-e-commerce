package com.ecommerce.catalog.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "catalog_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogOutboxEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "product_version", nullable = false)
  private Long productVersion;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  @Column(name = "aggregate_version", nullable = false)
  private Long aggregateVersion;

  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion;

  @Column(nullable = false)
  private String topic;

  @Column(name = "partition_key", nullable = false)
  private String partitionKey;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(nullable = false)
  private String status = "PENDING";

  @Column(nullable = false)
  private Integer attempts = 0;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "claimed_by")
  private String claimedBy;

  @Column(name = "lease_until")
  private Instant leaseUntil;

  public CatalogOutboxEvent(Product product, String eventType) {
    eventId = UUID.randomUUID();
    productId = product.getId();
    productVersion = product.getSearchVersion();
    aggregateType = "PRODUCT";
    aggregateId = productId;
    aggregateVersion = productVersion;
    schemaVersion = 1;
    topic = "catalog.search.index";
    partitionKey = String.valueOf(productId);
    payload = "{\"productId\":" + productId + ",\"productVersion\":" + productVersion + "}";
    this.eventType = eventType;
    occurredAt = createdAt = nextAttemptAt = Instant.now();
  }

  public void complete() {
    status = "PUBLISHED";
    processedAt = Instant.now();
    lastError = null;
    claimedBy = null;
    leaseUntil = null;
  }

  public void fail(String error) {
    attempts++;
    lastError = error;
    claimedBy = null;
    leaseUntil = null;
    status = attempts >= 8 ? "FAILED" : "PENDING";
    nextAttemptAt = Instant.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
  }
}
