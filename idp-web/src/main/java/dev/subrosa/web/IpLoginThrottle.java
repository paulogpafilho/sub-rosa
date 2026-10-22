package dev.subrosa.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Per-instance sliding window. Uses the socket peer, never untrusted forwarding headers. */
final class IpLoginThrottle {
  private final int limit;
  private final Duration window;
  private final int capacity;
  private final Clock clock;
  private final Map<String, ArrayDeque<Instant>> attempts = new HashMap<>();

  IpLoginThrottle(int limit, Duration window, int capacity, Clock clock) {
    this.limit = limit;
    this.window = window;
    this.capacity = capacity;
    this.clock = clock;
  }

  synchronized boolean allow(String address) {
    var cutoff = clock.instant().minus(window);
    attempts
        .values()
        .forEach(
            queue -> {
              while (!queue.isEmpty() && !queue.peekFirst().isAfter(cutoff)) {
                queue.removeFirst();
              }
            });
    attempts.values().removeIf(ArrayDeque::isEmpty);
    var queue = attempts.get(address);
    if (queue == null) {
      if (attempts.size() >= capacity) {
        return false;
      }
      queue = new ArrayDeque<>();
      attempts.put(address, queue);
    }
    if (queue.size() >= limit) {
      return false;
    }
    queue.addLast(clock.instant());
    return true;
  }
}
