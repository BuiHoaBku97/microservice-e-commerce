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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Generated(event = EventType.INSERT)
  @Column(name = "order_number", nullable = false, unique = true, length = 40, insertable = false, updatable = false)
  private String orderNumber;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status = OrderStatus.PENDING_PAYMENT;

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

  @Column(name = "accepted_payment_id")
  private Long acceptedPaymentId;

  @Column(name = "cancellation_reason", length = 50)
  private String cancellationReason;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "shipping_at")
  private Instant shippingAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "idempotency_key", length = 100)
  private String idempotencyKey;

  @Column(name = "idempotency_hash", length = 64)
  private String idempotencyHash;

  @Column(name = "shipping_address_id")
  private Long shippingAddressId;

  @Column(name = "shipping_recipient_name", length = 150)
  private String shippingRecipientName;

  @Column(name = "shipping_recipient_phone", length = 30)
  private String shippingRecipientPhone;

  @Column(name = "shipping_address_line", length = 255)
  private String shippingAddressLine;

  @Column(name = "shipping_ward", length = 100)
  private String shippingWard;

  @Column(name = "shipping_district", length = 100)
  private String shippingDistrict;

  @Column(name = "shipping_province", length = 100)
  private String shippingProvince;

  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<OrderItem> items = new ArrayList<>();

  public Order(Long userId, Long totalAmount, String currency, Instant paymentDueAt) {
    this.userId = userId;
    this.totalAmount = totalAmount;
    this.currency = currency;
    this.paymentDueAt = paymentDueAt;
  }

  public boolean isPaymentExpiredAt(Instant decisionTime) {
    Objects.requireNonNull(decisionTime, "decisionTime");
    return !decisionTime.isBefore(paymentDueAt);
  }

  public void addItem(OrderItem item) {
    item.assignOrder(this);
    items.add(item);
  }

  public void setIdempotency(String idempotencyKey, String idempotencyHash) {
    this.idempotencyKey = idempotencyKey;
    this.idempotencyHash = idempotencyHash;
  }

  public void setShippingAddress(
      Long addressId,
      String recipientName,
      String recipientPhone,
      String addressLine,
      String ward,
      String district,
      String province) {
    this.shippingAddressId = addressId;
    this.shippingRecipientName = recipientName;
    this.shippingRecipientPhone = recipientPhone;
    this.shippingAddressLine = addressLine;
    this.shippingWard = ward;
    this.shippingDistrict = district;
    this.shippingProvince = province;
  }

  public void cancel(Instant decisionTime) {
    if (status != OrderStatus.PENDING_PAYMENT) {
      throw new IllegalStateException("ORDER_CANNOT_BE_CANCELLED");
    }
    status = OrderStatus.CANCELLED;
    cancellationReason = "CUSTOMER_CANCELLED";
    cancelledAt = Objects.requireNonNull(decisionTime, "decisionTime");
  }

  public void markPaymentFailed(Instant decisionTime) {
    if (status == OrderStatus.PENDING_PAYMENT) {
      status = OrderStatus.CANCELLED;
      cancellationReason = "PAYMENT_FAILED";
      cancelledAt = Objects.requireNonNull(decisionTime, "decisionTime");
    }
  }

  public void markPaymentMismatch(Instant decisionTime) {
    if (status == OrderStatus.PENDING_PAYMENT) {
      status = OrderStatus.CANCELLED;
      cancellationReason = "PAYMENT_MISMATCH";
      cancelledAt = Objects.requireNonNull(decisionTime, "decisionTime");
    }
  }

  public void markPaymentExpired(Instant decisionTime) {
    if (status == OrderStatus.PENDING_PAYMENT) {
      status = OrderStatus.CANCELLED;
      cancellationReason = "PAYMENT_EXPIRED";
      cancelledAt = Objects.requireNonNull(decisionTime, "decisionTime");
    }
  }

  public void confirm(Long paymentId, Instant decisionTime) {
    if (status == OrderStatus.PENDING_PAYMENT) {
      status = OrderStatus.CONFIRMED;
      acceptedPaymentId = paymentId;
      confirmedAt = Objects.requireNonNull(decisionTime, "decisionTime");
    }
  }

  public void ship(Instant decisionTime) {
    if (status == OrderStatus.SHIPPING) return;
    if (status != OrderStatus.CONFIRMED) throw new IllegalStateException("INVALID_STATE_TRANSITION");
    status = OrderStatus.SHIPPING;
    shippingAt = Objects.requireNonNull(decisionTime, "decisionTime");
  }

  public void complete(Instant decisionTime) {
    if (status == OrderStatus.COMPLETED) return;
    if (status != OrderStatus.SHIPPING) throw new IllegalStateException("INVALID_STATE_TRANSITION");
    status = OrderStatus.COMPLETED;
    completedAt = Objects.requireNonNull(decisionTime, "decisionTime");
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
