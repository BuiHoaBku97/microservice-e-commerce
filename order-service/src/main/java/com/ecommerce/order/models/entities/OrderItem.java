package com.ecommerce.order.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(nullable = false)
  private Long skuId;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false)
  private Long unitPrice;

  @Column(nullable = false)
  private Long lineTotal;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  public OrderItem(Long skuId, Integer quantity, Long unitPrice) {
    this.skuId = skuId;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.lineTotal = unitPrice * quantity;
  }

  void assignOrder(Order order) {
    this.order = order;
  }

  @jakarta.persistence.PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }
}
