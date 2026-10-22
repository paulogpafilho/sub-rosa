package dev.subrosa.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserEntity {
  protected UserEntity() {}

  @Id UUID id;

  @Column(nullable = false, unique = true, length = 200)
  String username;

  @Column(length = 320)
  String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  String passwordHash;

  @Column(nullable = false)
  boolean enabled;

  @Column(name = "failed_attempts", nullable = false)
  int failedAttempts;

  @Column(name = "locked_until")
  Instant lockedUntil;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;
}
