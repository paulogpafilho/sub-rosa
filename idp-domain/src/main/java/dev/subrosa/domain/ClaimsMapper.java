package dev.subrosa.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Protocol-neutral values shared by OIDC and SAML. */
public final class ClaimsMapper {
  /** Returns protocol-neutral identity and access claims filtered by the granted scopes. */
  public Map<String, Object> map(User user, EffectiveAccess access, Set<String> scopes) {
    if (!user.enabled()) {
      throw new IllegalArgumentException("User unavailable");
    }
    var claims = new HashMap<String, Object>();
    claims.put("sub", user.id().toString());
    if (scopes.contains("profile")) {
      claims.put("preferred_username", user.username());
    }
    if (scopes.contains("email") && user.email() != null) {
      claims.put("email", user.email());
    }
    if (scopes.contains("groups")) {
      claims.put("groups", access.groups().stream().sorted().toList());
    }
    if (scopes.contains("roles")) {
      claims.put("roles", access.roles().stream().sorted().toList());
      claims.put(
          "entitlements", access.entitlements().stream().map(Entitlement::value).sorted().toList());
    }
    return Map.copyOf(claims);
  }
}
