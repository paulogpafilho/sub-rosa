package dev.subrosa.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimsMapperTest {
  @Test
  void filtersIdentityAndAccessByScopeAndNeverLeaksCredentialState() {
    var user =
        new User(
            UUID.randomUUID(),
            "alice",
            "alice@example.test",
            "redacted-hash",
            true,
            0,
            null,
            Instant.EPOCH);
    var access =
        new EffectiveAccess(
            Set.of("Reader"), Set.of(new Entitlement("invoices:read")), Set.of("Finance"));
    var mapper = new ClaimsMapper();
    assertEquals(
        java.util.Map.of("sub", user.id().toString()), mapper.map(user, access, Set.of("openid")));
    var claims = mapper.map(user, access, Set.of("profile", "email", "groups", "roles"));
    assertEquals("alice", claims.get("preferred_username"));
    assertEquals("alice@example.test", claims.get("email"));
    assertEquals(java.util.List.of("invoices:read"), claims.get("entitlements"));
    assertFalse(claims.containsKey("passwordHash"));
    assertThrows(UnsupportedOperationException.class, () -> claims.put("admin", true));
    var disabled =
        new User(
            user.id(),
            user.username(),
            user.email(),
            user.passwordHash(),
            false,
            0,
            null,
            user.createdAt());
    assertThrows(
        IllegalArgumentException.class, () -> mapper.map(disabled, access, Set.of("openid")));
  }
}
