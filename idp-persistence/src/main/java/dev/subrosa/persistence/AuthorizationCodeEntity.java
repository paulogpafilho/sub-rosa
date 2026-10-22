package dev.subrosa.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "authorization_codes")
class AuthorizationCodeEntity {
  protected AuthorizationCodeEntity() {}

  @Id
  @Column(name = "code_hash", length = 64)
  String hash;

  @Column(nullable = false)
  UUID familyId;

  @Column(nullable = false, length = 200)
  String clientId;

  @Column(nullable = false)
  UUID userId;

  @Column(nullable = false, length = 2000)
  String redirectUri;

  @Column(nullable = false, length = 500)
  String scopes;

  @Column(length = 2000)
  String nonce;

  @Column(name = "code_challenge", nullable = false, length = 43)
  String challenge;

  @Column(nullable = false)
  Instant authTime;

  @Column(nullable = false)
  Instant expiresAt;

  Instant consumedAt;
}
