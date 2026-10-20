package dev.subrosa.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SigningKeyTest {
	@Test
	void lifecyclePublishesRetiredKeysUntilExclusiveDeadline() {
		var now = Instant.parse("2026-10-20T00:00:00Z");
		var active = new SigningKey("key-1", "ciphertext", "public", "certificate", SigningKey.Status.ACTIVE, now,
				null);
		var until = now.plusSeconds(86400);
		var retired = active.beginRetirement(until);
		assertTrue(active.publishedAt(until));
		assertTrue(retired.publishedAt(until.minusNanos(1)));
		assertFalse(retired.publishedAt(until));
		assertThrows(IllegalStateException.class, () -> retired.beginRetirement(until));
		assertThrows(IllegalStateException.class, () -> retired.retire(until.minusNanos(1)));
		assertFalse(retired.retire(until).publishedAt(until.minusSeconds(1)));
		assertThrows(IllegalArgumentException.class, () -> active.beginRetirement(now));
		assertThrows(IllegalArgumentException.class,
				() -> new SigningKey("key-1", "enc", "pub", "cert", SigningKey.Status.ACTIVE, now, until));
		assertFalse(active.toString().contains("ciphertext"));
	}
}
