package dev.subrosa.oidc;

import java.time.Duration;

/** Global ceilings also bound how long old signing keys must be retained. */
public record OAuthSettings(
    String issuer, Duration codeTtl, Duration tokenTtl, Duration refreshTtl, Duration clockSkew) {
  /** Validates issuer and lifetime ceilings that also bound signing-key retention. */
  public OAuthSettings {
    if (issuer == null
        || issuer.endsWith("/")
        || !valid(codeTtl, Duration.ofSeconds(60))
        || !valid(tokenTtl, Duration.ofMinutes(10))
        || !valid(refreshTtl, Duration.ofHours(24))
        || clockSkew == null
        || clockSkew.isNegative()
        || clockSkew.compareTo(Duration.ofMinutes(1)) > 0) {
      throw new IllegalArgumentException("Invalid OAuth lifetime/issuer configuration");
    }
  }

  private static boolean valid(Duration duration, Duration ceiling) {
    return duration != null
        && !duration.isNegative()
        && !duration.isZero()
        && duration.getNano() == 0
        && duration.compareTo(ceiling) <= 0;
  }

  /** Returns the shorter lifetime when combining client and global limits. */
  public static Duration shorter(Duration first, Duration second) {
    return first.compareTo(second) < 0 ? first : second;
  }
}
