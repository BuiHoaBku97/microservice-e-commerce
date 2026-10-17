package com.ecommerce.payment.models.entities;

import com.ecommerce.payment.messaging.events.OrderCancelledEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_payment_contexts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaymentContext {
  @Id
  @Column(name = "order_id")
  private Long orderId;

  @Column(nullable = false)
  private boolean closed;

  @Column(name = "cancellation_reason", length = 50)
  private String cancellationReason;

  @Column(name = "cancellation_event_id", length = 100, unique = true)
  private String cancellationEventId;

  @Column(name = "cancellation_occurred_at")
  private Instant cancellationOccurredAt;

  @Column(name = "cancellation_version")
  private Long cancellationVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  public OrderPaymentContext(Long orderId) {
    this.orderId = orderId;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
    this.version = 0L;
  }

  public boolean close(OrderCancelledEvent event) {
    if (closed) {
      if (Objects.equals(cancellationReason, event.reason())
          && Objects.equals(cancellationVersion, event.aggregateVersion())) {
        return false;
      }
      throw new IllegalStateException("ORDER_CANCELLATION_CONTEXT_CONFLICT");
    }
    closed = true;
    cancellationReason = event.reason();
    cancellationEventId = event.eventId();
    cancellationOccurredAt = event.occurredAt();
    cancellationVersion = event.aggregateVersion();
    updatedAt = Instant.now();
    return true;
  }
}
