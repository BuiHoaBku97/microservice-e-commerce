package com.ecommerce.notification.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "delivery_attempts") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAttempt {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "notification_id", nullable = false) private Notification notification;
  @Column(nullable = false) private Integer attemptNumber;
  @Column(nullable = false, length = 20) private String outcome;
  @Column(columnDefinition = "text") private String error;
  @Column(nullable = false) private Instant attemptedAt;
  public DeliveryAttempt(Notification notification, int number, String outcome, String error) {
    this.notification = notification; this.attemptNumber = number; this.outcome = outcome;
    this.error = error; this.attemptedAt = Instant.now();
  }
}
