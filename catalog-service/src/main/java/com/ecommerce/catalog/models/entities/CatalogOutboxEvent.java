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

  public CatalogOutboxEvent(Product product, String eventType) {
    eventId = UUID.randomUUID();
    productId = product.getId();
    productVersion = product.getSearchVersion();
    this.eventType = eventType;
    createdAt = nextAttemptAt = Instant.now();
  }

  public void complete() {
    status = "PUBLISHED";
    processedAt = Instant.now();
    lastError = null;
  }

  public void fail(String error) {
    attempts++;
    lastError = error;
    status = attempts >= 8 ? "FAILED" : "PENDING";
    nextAttemptAt = Instant.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 8)));
  }
}
