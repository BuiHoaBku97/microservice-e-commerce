package com.ecommerce.payment.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "payment_callbacks") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCallback {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false, unique = true, length = 120) private String providerEventId;
  @Column(nullable = false, length = 64) private String payloadHash;
  @Column(nullable = false) private Long paymentId;
  @Column(nullable = false) private Instant receivedAt;
  public PaymentCallback(String providerEventId, String payloadHash, Long paymentId) {
    this.providerEventId = providerEventId; this.payloadHash = payloadHash;
    this.paymentId = paymentId; this.receivedAt = Instant.now();
  }
}
