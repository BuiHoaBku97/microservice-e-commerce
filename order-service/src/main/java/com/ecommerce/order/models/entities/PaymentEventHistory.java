package com.ecommerce.order.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_event_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEventHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String eventId;

  @Column(nullable = false)
  private Long paymentId;

  @Column(nullable = false)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PaymentEventResult result;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(nullable = false, updatable = false)
  private Instant receivedAt = Instant.now();

  public PaymentEventHistory(
      String eventId, Long paymentId, Long orderId, PaymentEventResult result, String reason) {
    this.eventId = eventId;
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.result = result;
    this.reason = reason;
  }
}
