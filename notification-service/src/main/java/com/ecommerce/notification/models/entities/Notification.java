package com.ecommerce.notification.models.entities;

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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false, length = 80)
  private String eventType;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1000)
  private String message;

  @Column(nullable = false, length = 80)
  private String referenceType;

  @Column(nullable = false)
  private Long referenceId;

  @Column(nullable = false, unique = true, length = 180)
  private String deduplicationKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationStatus status = NotificationStatus.PENDING;

  private Instant readAt;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public Notification(
      Long userId,
      String eventType,
      String title,
      String message,
      String referenceType,
      Long referenceId,
      String deduplicationKey) {
    this.userId = userId;
    this.eventType = eventType;
    this.title = title;
    this.message = message;
    this.referenceType = referenceType;
    this.referenceId = referenceId;
    this.deduplicationKey = deduplicationKey;
  }

  public void markSent() {
    status = NotificationStatus.SENT;
  }

  public void markRead() {
    readAt = Instant.now();
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
