package dev.subrosa.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IpLoginThrottleTest {
  @Test
  void slidingWindowBoundsBothAttemptsAndMemoryWithoutEvictingActiveLimits() {
    var clock = new MutableClock();
    var throttle = new IpLoginThrottle(2, Duration.ofMinutes(1), 2, clock);
    assertThat(throttle.allow("a")).isTrue();
    clock.now = clock.now.plusSeconds(30);
    assertThat(throttle.allow("a")).isTrue();
    assertThat(throttle.allow("a")).isFalse();
    assertThat(throttle.allow("b")).isTrue();
    assertThat(throttle.allow("c")).isFalse();
    clock.now = clock.now.plusSeconds(30);
    assertThat(throttle.allow("a")).isTrue();
    assertThat(throttle.allow("a")).isFalse();
    clock.now = clock.now.plusSeconds(60);
    assertThat(throttle.allow("c")).isTrue();
  }

  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
