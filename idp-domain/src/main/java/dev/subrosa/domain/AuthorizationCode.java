package dev.subrosa.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Hashed, client-bound authorization grant with a single-use expiry. */
public record AuthorizationCode(
    String hash,
    UUID familyId,
    String clientId,
    UUID userId,
    String redirectUri,
    Set<String> scopes,
    String nonce,
    String challenge,
    Instant authTime,
    Instant expiresAt) {

  /** Copies granted scopes so callers cannot mutate the stored grant snapshot. */
  public AuthorizationCode {
    scopes = Set.copyOf(scopes);
  }

  @Override
  public String toString() {
    return "AuthorizationCode[redacted]";
  }
}
