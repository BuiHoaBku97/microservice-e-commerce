package com.ecommerce.payment.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long orderId;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private Long amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status = PaymentStatus.PENDING;

  @Column(length = 120)
  private String providerReference;

  @Column(length = 500)
  private String failureReason;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  public Payment(Long orderId, Long userId, Long amount, String currency) {
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
  }

  public void complete(String providerReference) {
    status = PaymentStatus.COMPLETED;
    this.providerReference = providerReference;
    failureReason = null;
  }

  public void fail(String reason) {
    status = PaymentStatus.FAILED;
    failureReason = reason;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
