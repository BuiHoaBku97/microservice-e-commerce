package com.ecommerce.identity.repositories;

import com.ecommerce.identity.models.entities.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select token from RefreshToken token join fetch token.user where token.tokenHash = :hash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select token from RefreshToken token where token.sessionId = :sessionId and token.revokedAt"
          + " is null")
  List<RefreshToken> findActiveBySessionIdForUpdate(@Param("sessionId") UUID sessionId);
}
