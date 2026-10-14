package com.ecommerce.catalog.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "catalog_reindex_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogReindexJob {
  public enum Status {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.PENDING;

  @Column(name = "requested_by", nullable = false)
  private Long requestedBy;

  @Column(name = "target_index", nullable = false)
  private String targetIndex;

  @Column(name = "alias_name", nullable = false)
  private String aliasName;

  @Column(name = "total_count", nullable = false)
  private Long totalCount = 0L;

  @Column(name = "processed_count", nullable = false)
  private Long processedCount = 0L;

  @Column(name = "failed_count", nullable = false)
  private Long failedCount = 0L;

  @Column(nullable = false)
  private Long checkpoint = 0L;

  @Column(name = "error_summary")
  private String errorSummary;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version = 0L;

  public CatalogReindexJob(Long requestedBy, long total) {
    this.requestedBy = requestedBy;
    totalCount = total;
    createdAt = updatedAt = Instant.now();
    targetIndex = "catalog-products-" + createdAt.toEpochMilli();
    aliasName = "catalog-products";
  }

  public void start() {
    if (status == Status.PENDING) {
      status = Status.RUNNING;
      startedAt = updatedAt = Instant.now();
    }
  }

  public void progress(Long id) {
    checkpoint = id;
    processedCount++;
    updatedAt = Instant.now();
  }

  public void failItem(String error) {
    failedCount++;
    errorSummary = error;
    updatedAt = Instant.now();
  }

  public void complete() {
    status = failedCount == 0 ? Status.COMPLETED : Status.FAILED;
    completedAt = updatedAt = Instant.now();
  }

  public void fail(String error) {
    status = Status.FAILED;
    errorSummary = error;
    completedAt = updatedAt = Instant.now();
  }
}
