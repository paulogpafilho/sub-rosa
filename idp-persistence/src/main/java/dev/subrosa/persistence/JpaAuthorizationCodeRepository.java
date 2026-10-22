package dev.subrosa.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface JpaAuthorizationCodeRepository extends JpaRepository<AuthorizationCodeEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from AuthorizationCodeEntity c where c.hash = :hash")
  Optional<AuthorizationCodeEntity> locked(String hash);

  @Modifying
  @Query(
      "update AuthorizationCodeEntity c set c.consumedAt = :now"
          + " where c.hash = :hash and c.consumedAt is null and c.expiresAt > :now")
  int consume(String hash, Instant now);
}
