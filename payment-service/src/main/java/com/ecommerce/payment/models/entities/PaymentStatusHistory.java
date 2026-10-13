package com.ecommerce.payment.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentStatusHistory {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private PaymentStatus status;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private FundsStatus fundsStatus;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private OrderAcceptance orderAcceptance;
  @Column(nullable = false, length = 50) private String source;
  @Column(length = 200) private String reason;
  @Column(nullable = false, updatable = false) private Instant createdAt;

  public PaymentStatusHistory(Payment payment, String source, String reason) {
    this.payment = payment;
    this.status = payment.getStatus();
    this.fundsStatus = payment.getFundsStatus();
    this.orderAcceptance = payment.getOrderAcceptance();
    this.source = source;
    this.reason = reason;
    this.createdAt = Instant.now();
  }
}
