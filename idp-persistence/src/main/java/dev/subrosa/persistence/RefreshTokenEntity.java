package dev.subrosa.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity {
  protected RefreshTokenEntity() {}

  @Id
  @Column(name = "token_hash", length = 64)
  String hash;

  @Column(nullable = false)
  UUID familyId;

  @Column(nullable = false, length = 200)
  String clientId;

  @Column(nullable = false)
  UUID userId;

  @Column(nullable = false, length = 500)
  String scopes;

  @Column(length = 2000)
  String nonce;

  @Column(nullable = false)
  Instant authTime;

  @Column(nullable = false)
  Instant expiresAt;

  Instant rotatedAt;
}
