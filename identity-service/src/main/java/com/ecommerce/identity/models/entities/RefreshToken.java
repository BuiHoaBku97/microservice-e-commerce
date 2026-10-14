package com.ecommerce.identity.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by_hash", length = 64)
  private String replacedByHash;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "replaced_by_id")
  private RefreshToken replacedBy;

  public RefreshToken(User user, String tokenHash, Instant expiresAt) {
    this(user, tokenHash, expiresAt, UUID.randomUUID());
  }

  public RefreshToken(User user, String tokenHash, Instant expiresAt, UUID sessionId) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.sessionId = sessionId;
    this.createdAt = Instant.now();
  }

  public boolean isUsableAt(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void revoke(String replacementHash) {
    if (revokedAt == null) {
      revokedAt = Instant.now();
      replacedByHash = replacementHash;
    }
  }

  public void replaceWith(RefreshToken replacement) {
    revoke(replacement.getTokenHash());
    replacedBy = replacement;
  }
}
