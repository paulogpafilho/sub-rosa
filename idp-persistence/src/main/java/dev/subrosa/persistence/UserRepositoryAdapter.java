package dev.subrosa.persistence;

import dev.subrosa.domain.User;
import dev.subrosa.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Maps user snapshots and applies atomic authentication updates through JPA. */
@Repository
public class UserRepositoryAdapter implements UserRepository {
  private final JpaUserRepository users;
  private final JdbcTemplate jdbc;

  /** Uses JPA persistence and JDBC bootstrap serialization for identities. */
  public UserRepositoryAdapter(JpaUserRepository users, JdbcTemplate jdbc) {
    this.users = users;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> findById(UUID id) {
    return users.findById(id).map(UserMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> findByUsername(String username) {
    return users.findByUsername(username).map(UserMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasUsers() {
    return users.count() != 0;
  }

  @Override
  @Transactional
  public boolean createFirst(User user) {
    // Serialize bootstrap across instances without relying on a username conflict.
    jdbc.execute("SELECT pg_advisory_xact_lock(72519301)");
    if (hasUsers()) {
      return false;
    }
    users.saveAndFlush(UserMapper.toEntity(user));
    return true;
  }

  @Override
  @Transactional
  public boolean recordFailure(UUID id, Instant now, int threshold, Duration lockDuration) {
    return users.recordFailure(id, now, threshold, now.plus(lockDuration)) == 1;
  }

  @Override
  @Transactional
  public boolean recordSuccess(User verified, Instant now) {
    return users.recordSuccess(
            verified.id(), verified.passwordHash(), verified.failedAttempts(), now)
        == 1;
  }
}
