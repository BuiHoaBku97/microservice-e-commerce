package com.ecommerce.order.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistory {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false) private Order order;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private OrderStatus status;
  @Column(nullable = false, length = 50) private String source;
  @Column(length = 100) private String reason;
  @Column(nullable = false, updatable = false) private Instant createdAt;

  public OrderStatusHistory(Order order, String source, String reason) {
    this.order = order;
    this.status = order.getStatus();
    this.source = source;
    this.reason = reason;
    this.createdAt = Instant.now();
  }
}
