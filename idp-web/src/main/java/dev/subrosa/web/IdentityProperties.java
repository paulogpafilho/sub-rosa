package dev.subrosa.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Validated authentication, bootstrap, throttling and session configuration. */
@Validated
@ConfigurationProperties("idp.identity")
public record IdentityProperties(
    @Min(10) @Max(16) int bcryptCost,
    @Min(1) @Max(72) int passwordMaxBytes,
    @Min(1) int lockoutFailures,
    @NotNull Duration lockoutDuration,
    @Min(1) int ipAttempts,
    @NotNull Duration ipWindow,
    @Min(1) int ipCapacity,
    @NotNull Duration sessionIdleTimeout,
    @NotNull Duration sessionAbsoluteTimeout,
    String adminUser,
    String adminPassword) {
  @AssertTrue(message = "Identity timeouts must be positive whole seconds within supported bounds")
  public boolean isDurationValid() {
    return positive(lockoutDuration)
        && positive(ipWindow)
        && positive(sessionIdleTimeout)
        && positive(sessionAbsoluteTimeout);
  }

  private static boolean positive(Duration duration) {
    return duration != null
        && duration.getSeconds() > 0
        && duration.getSeconds() <= Integer.MAX_VALUE
        && duration.getNano() == 0;
  }

  @Override
  public String toString() {
    return "IdentityProperties[credentials redacted]";
  }
}
