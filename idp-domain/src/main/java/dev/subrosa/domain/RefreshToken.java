package dev.subrosa.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Hashed refresh credential belonging to an absolutely expiring token family. */
public record RefreshToken(
    String hash,
    UUID familyId,
    String clientId,
    UUID userId,
    Set<String> scopes,
    String nonce,
    Instant authTime,
    Instant expiresAt) {
  /** Copies granted scopes so callers cannot mutate the refresh snapshot. */
  public RefreshToken {
    scopes = Set.copyOf(scopes);
  }

  @Override
  public String toString() {
    return "RefreshToken[redacted]";
  }
}
