package dev.subrosa.persistence;

import dev.subrosa.domain.SigningKey;
import dev.subrosa.domain.SigningKeyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Serializes signing-key initialization and rotation with PostgreSQL locks. */
@Repository
public class SigningKeyRepositoryAdapter implements SigningKeyRepository {
  private final JpaSigningKeyRepository keys;
  private final JdbcTemplate jdbc;

  /** Uses JPA persistence and JDBC transaction locks for key transitions. */
  public SigningKeyRepositoryAdapter(JpaSigningKeyRepository keys, JdbcTemplate jdbc) {
    this.keys = keys;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SigningKey> active() {
    return keys.findByStatus("ACTIVE").map(SigningKeyEntity::snapshot);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SigningKey> publishedAt(Instant now) {
    return keys.publishedAt(now).stream().map(SigningKeyEntity::snapshot).toList();
  }

  @Override
  @Transactional
  public SigningKey initialize(Supplier<SigningKey> factory) {
    jdbc.execute("SELECT pg_advisory_xact_lock(72519302)");
    return active()
        .orElseGet(
            () -> {
              if (keys.count() != 0) {
                throw new IllegalStateException("Signing key history exists without an active key");
              }
              return insert(factory.get());
            });
  }

  @Override
  @Transactional
  public SigningKey rotate(Supplier<SigningKey> factory, Instant now, Instant retireAt) {
    if (!retireAt.isAfter(now)) {
      throw new IllegalArgumentException("Retention must end in the future");
    }
    jdbc.execute("SELECT pg_advisory_xact_lock(72519302)");
    var previous = keys.findByStatus("ACTIVE").orElseThrow();
    var retired = previous.snapshot().beginRetirement(retireAt);
    previous.status = retired.status().name();
    previous.retireAt = retired.retireAt();
    keys.flush();
    return insert(factory.get());
  }

  private SigningKey insert(SigningKey key) {
    if (key.status() != SigningKey.Status.ACTIVE) {
      throw new IllegalArgumentException("A new key must be active");
    }
    if (keys.existsById(key.kid())) {
      throw new IllegalArgumentException("A replacement must have a new key ID");
    }
    keys.saveAndFlush(SigningKeyEntity.from(key));
    return key;
  }
}
