package dev.subrosa.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface JpaRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
  @Modifying
  @Query(
      "update RefreshTokenEntity t set t.rotatedAt = :now"
          + " where t.hash = :hash and t.rotatedAt is null and t.expiresAt > :now")
  int rotate(String hash, Instant now);
}
