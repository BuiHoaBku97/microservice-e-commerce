package com.ecommerce.order.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReservationStatus status = ReservationStatus.ACTIVE;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "committed_at")
  private Instant committedAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "release_reason", length = 100)
  private String releaseReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ReservationItem> items = new ArrayList<>();

  public Reservation(Long orderId, Instant expiresAt) {
    this.orderId = orderId;
    this.expiresAt = expiresAt;
  }

  public void addItem(Long skuId, int quantity) {
    items.add(new ReservationItem(this, skuId, quantity));
  }

  public boolean commit() {
    if (status == ReservationStatus.COMMITTED) {
      return false;
    }
    if (status != ReservationStatus.ACTIVE) {
      throw new IllegalStateException("RESERVATION_ALREADY_RELEASED");
    }
    status = ReservationStatus.COMMITTED;
    committedAt = Instant.now();
    return true;
  }

  public boolean release(String reason) {
    if (status == ReservationStatus.RELEASED) {
      return false;
    }
    if (status != ReservationStatus.ACTIVE) {
      throw new IllegalStateException("RESERVATION_ALREADY_COMMITTED");
    }
    status = ReservationStatus.RELEASED;
    releasedAt = Instant.now();
    releaseReason = reason;
    return true;
  }

  @PrePersist
  void create() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void update() {
    updatedAt = Instant.now();
  }
}
