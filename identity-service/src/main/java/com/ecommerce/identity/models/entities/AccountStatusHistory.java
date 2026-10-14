package com.ecommerce.identity.models.entities;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", nullable = false)
  private UserStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false)
  private UserStatus toStatus;

  @Column(length = 200)
  private String reason;

  @Column(nullable = false, length = 150)
  private String actor;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public AccountStatusHistory(
      Long userId, UserStatus fromStatus, UserStatus toStatus, String reason, String actor) {
    this.userId = userId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.reason = reason;
    this.actor = actor;
    this.createdAt = Instant.now();
  }
}
