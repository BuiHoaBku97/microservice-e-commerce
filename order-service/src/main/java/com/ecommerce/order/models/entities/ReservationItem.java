package com.ecommerce.order.models.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "reservation_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"reservation_id", "sku_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reservation_id", nullable = false)
  private Reservation reservation;

  @Column(name = "sku_id", nullable = false)
  private Long skuId;

  @Column(nullable = false)
  private Integer quantity;

  ReservationItem(Reservation reservation, Long skuId, Integer quantity) {
    if (quantity == null || quantity <= 0)
      throw new IllegalArgumentException("INVALID_RESERVATION_QUANTITY");
    this.reservation = reservation;
    this.skuId = skuId;
    this.quantity = quantity;
  }
}
