package com.ecommerce.catalog.models.entities;

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
@Table(name = "sku_price_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkuPriceHistory {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sku_id", nullable = false)
  private Sku sku;
  @Column(name = "old_price") private Long oldPrice;
  @Column(name = "new_price", nullable = false) private Long newPrice;
  @Column(length = 200) private String reason;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

  public SkuPriceHistory(Sku sku, Long oldPrice, Long newPrice, String reason) {
    this.sku = sku;
    this.oldPrice = oldPrice;
    this.newPrice = newPrice;
    this.reason = reason;
    this.createdAt = Instant.now();
  }
}
