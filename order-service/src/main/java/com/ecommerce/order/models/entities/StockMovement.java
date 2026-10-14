package com.ecommerce.order.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sku_id", nullable = false)
  private Long skuId;

  @Enumerated(EnumType.STRING)
  @Column(name = "movement_type", nullable = false)
  private StockMovementType movementType;

  @Column(name = "on_hand_delta", nullable = false)
  private Integer onHandDelta;

  @Column(name = "reserved_delta", nullable = false)
  private Integer reservedDelta;

  @Column(name = "on_hand_after", nullable = false)
  private Integer onHandAfter;

  @Column(name = "reserved_after", nullable = false)
  private Integer reservedAfter;

  @Column(name = "order_id")
  private Long orderId;

  @Column(name = "reservation_id")
  private Long reservationId;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "source_event_id", length = 100)
  private String sourceEventId;

  @Column(name = "operation_id", nullable = false)
  private UUID operationId;

  @Column(length = 200)
  private String reason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public StockMovement(
      Inventory inventory,
      StockMovementType type,
      int onHandDelta,
      int reservedDelta,
      Long orderId,
      Long reservationId,
      Long actorUserId,
      String sourceEventId,
      UUID operationId,
      String reason) {
    this.skuId = inventory.getSkuId();
    this.movementType = type;
    this.onHandDelta = onHandDelta;
    this.reservedDelta = reservedDelta;
    this.onHandAfter = inventory.getOnHand();
    this.reservedAfter = inventory.getReserved();
    this.orderId = orderId;
    this.reservationId = reservationId;
    this.actorUserId = actorUserId;
    this.sourceEventId = sourceEventId;
    this.operationId = operationId;
    this.reason = reason;
    this.createdAt = Instant.now();
  }
}
