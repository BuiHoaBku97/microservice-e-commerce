package com.ecommerce.order.models.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status = OrderStatus.PENDING;

  @Column(nullable = false)
  private Long totalAmount;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(nullable = false, updatable = false)
  private Instant paymentDueAt;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Column(name = "idempotency_key", length = 100)
  private String idempotencyKey;

  @Column(name = "idempotency_hash", length = 64)
  private String idempotencyHash;

  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<OrderItem> items = new ArrayList<>();

  public Order(Long userId, Long totalAmount, String currency) {
    this(userId, totalAmount, currency, Instant.now().plusSeconds(900));
  }

  public Order(Long userId, Long totalAmount, String currency, Instant paymentDueAt) {
    this.userId = userId;
    this.totalAmount = totalAmount;
    this.currency = currency;
    this.paymentDueAt = paymentDueAt;
  }

  public void addItem(OrderItem item) {
    item.assignOrder(this);
    items.add(item);
  }

  public void setIdempotency(String idempotencyKey, String idempotencyHash) {
    this.idempotencyKey = idempotencyKey;
    this.idempotencyHash = idempotencyHash;
  }

  public void cancel() {
    if (status != OrderStatus.PENDING) {
      throw new IllegalStateException("ORDER_CANNOT_BE_CANCELLED");
    }
    status = OrderStatus.CANCELLED;
  }

  public void markPaid() {
    if (status == OrderStatus.PENDING) {
      status = OrderStatus.PAID;
    }
  }

  public void markPaymentFailed() {
    if (status == OrderStatus.PENDING) {
      status = OrderStatus.PAYMENT_FAILED;
    }
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
