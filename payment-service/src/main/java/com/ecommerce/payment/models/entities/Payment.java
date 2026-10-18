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

  @Column(nullable = false, length = 30)
  private String paymentMethod = "MOCK_ONLINE";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status = PaymentStatus.PENDING;

  @Column(length = 120)
  private String providerReference;

  @Column(length = 500)
  private String failureReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FundsStatus fundsStatus = FundsStatus.NOT_RECEIVED;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderAcceptance orderAcceptance = OrderAcceptance.PENDING;

  @Column(length = 120)
  private String providerTransactionId;

  private Instant paidAt;

  private Instant failedAt;

  @Column(length = 100)
  private String reviewReason;

  @Column(nullable = false, updatable = false)
  private Instant paymentDueAt;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  public Payment(Long orderId, Long userId, Long amount, String currency) {
    this(orderId, userId, amount, currency, Instant.now().plusSeconds(900));
  }

  public Payment(Long orderId, Long userId, Long amount, String currency, Instant paymentDueAt) {
    this(orderId, userId, amount, currency, paymentDueAt, "MOCK_ONLINE");
  }

  public Payment(Long orderId, Long userId, Long amount, String currency, Instant paymentDueAt,
      String paymentMethod) {
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.currency = currency;
    this.paymentDueAt = paymentDueAt;
    this.paymentMethod = paymentMethod;
  }

  public static Payment closedFromOrder(
      Long orderId,
      Long userId,
      Long amount,
      String currency,
      Instant paymentDueAt,
      String cancellationReason) {
    Payment payment = new Payment(orderId, userId, amount, currency, paymentDueAt);
    payment.status = statusForCancellationReason(cancellationReason);
    payment.orderAcceptance = OrderAcceptance.REJECTED;
    payment.failureReason =
        "PAYMENT_FAILED".equals(cancellationReason) ? "PAYMENT_FAILED" : null;
    return payment;
  }

  public void complete(String providerReference) {
    status = PaymentStatus.SUCCESS;
    fundsStatus = FundsStatus.RECEIVED;
    this.providerReference = providerReference;
    this.providerTransactionId = providerReference;
    this.paidAt = Instant.now();
    failureReason = null;
  }

  public boolean receive(String providerTransactionId, Instant paidAt) {
    if (fundsStatus == FundsStatus.RECEIVED) return false;
    PaymentStatus previousStatus = status;
    PaymentStatus nextStatus;
    if (orderAcceptance == OrderAcceptance.REJECTED
        || previousStatus == PaymentStatus.FAILED
        || previousStatus == PaymentStatus.EXPIRED
        || previousStatus == PaymentStatus.CANCELLED) {
      nextStatus = PaymentStatus.REQUIRES_REVIEW;
    } else if (previousStatus == PaymentStatus.PENDING) {
      nextStatus = PaymentStatus.SUCCESS;
    } else {
      throw new IllegalStateException("INVALID_PAYMENT_STATE");
    }
    fundsStatus = FundsStatus.RECEIVED;
    this.providerTransactionId = providerTransactionId;
    this.providerReference = providerTransactionId;
    this.paidAt = paidAt == null ? Instant.now() : paidAt;
    status = nextStatus;
    failureReason = null;
    return true;
  }

  public void fail(String reason) {
    if (fundsStatus == FundsStatus.RECEIVED) return;
    status = PaymentStatus.FAILED;
    failureReason = reason;
    failedAt = Instant.now();
  }

  public void acceptOrder() {
    orderAcceptance = OrderAcceptance.ACCEPTED;
    if (fundsStatus == FundsStatus.RECEIVED) status = PaymentStatus.SUCCESS;
  }

  public void rejectOrder(String reason) {
    orderAcceptance = OrderAcceptance.REJECTED;
    reviewReason = reason;
    if (fundsStatus == FundsStatus.RECEIVED) status = PaymentStatus.REQUIRES_REVIEW;
  }

  public void cancelFromOrder(String reason) {
    PaymentStatus cancellationStatus = statusForCancellationReason(reason);
    orderAcceptance = OrderAcceptance.REJECTED;
    if (fundsStatus == FundsStatus.RECEIVED) {
      status = PaymentStatus.REQUIRES_REVIEW;
      reviewReason = reason;
    } else {
      status = cancellationStatus;
    }
  }

  private static PaymentStatus statusForCancellationReason(String reason) {
    return switch (reason) {
      case "PAYMENT_FAILED" -> PaymentStatus.FAILED;
      case "PAYMENT_EXPIRED" -> PaymentStatus.EXPIRED;
      case "CUSTOMER_CANCELLED" -> PaymentStatus.CANCELLED;
      default -> throw new IllegalArgumentException("ORDER_CANCELLATION_REASON_INVALID");
    };
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
