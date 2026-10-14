package com.ecommerce.order.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory_idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryIdempotencyRecord {
  public enum Status {
    PROCESSING,
    COMPLETED,
    FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "request_fingerprint", nullable = false)
  private String requestFingerprint;

  @Column(name = "operation_type", nullable = false)
  private String operationType;

  @Column(name = "sku_id", nullable = false)
  private Long skuId;

  @Column(name = "response_status")
  private Integer responseStatus;

  @Column(name = "response_body", columnDefinition = "text")
  private String responseBody;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public InventoryIdempotencyRecord(
      String key, String fingerprint, String operationType, Long skuId) {
    this.idempotencyKey = key;
    this.requestFingerprint = fingerprint;
    this.operationType = operationType;
    this.skuId = skuId;
    this.status = Status.PROCESSING;
    this.createdAt = this.updatedAt = Instant.now();
    this.expiresAt = createdAt.plusSeconds(86400 * 30L);
  }

  public void complete(String responseBody) {
    status = Status.COMPLETED;
    responseStatus = 200;
    this.responseBody = responseBody;
    updatedAt = Instant.now();
  }
}
