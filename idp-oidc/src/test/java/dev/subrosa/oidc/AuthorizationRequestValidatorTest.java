package dev.subrosa.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.subrosa.domain.OAuthClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationRequestValidatorTest {
  final OAuthClient client =
      new OAuthClient(
          "public",
          UUID.randomUUID(),
          null,
          OAuthClient.AuthMethod.NONE,
          Set.of("authorization_code", "refresh_token"),
          Set.of("openid", "profile", "offline_access"),
          Set.of("http://localhost:8081/callback?fixed=1"),
          Duration.ofMinutes(10),
          Duration.ofHours(24),
          null);
  final AuthorizationRequestValidator validator = new AuthorizationRequestValidator();

  Map<String, String> request() {
    return new HashMap<>(
        Map.of(
            "response_type",
            "code",
            "redirect_uri",
            client.redirectUris().iterator().next(),
            "scope",
            "openid profile",
            "code_challenge",
            "a".repeat(43),
            "code_challenge_method",
            "S256"));
  }

  @Test
  void validatesExactRedirectAndRequiredS256() {
    assertEquals(Set.of("openid", "profile"), validator.validate(client, request()).scopes());
    for (String bad :
        new String[] {
          "http://localhost:8081/callback", "http://localhost:8081/callback?fixed=1&extra=2"
        }) {
      var input = request();
      input.put("redirect_uri", bad);
      assertThrows(OAuthError.class, () -> validator.validate(client, input));
    }
    for (String field : new String[] {"code_challenge", "code_challenge_method"}) {
      var input = request();
      input.remove(field);
      assertThrows(OAuthError.class, () -> validator.validate(client, input));
    }
    var input = request();
    input.put("code_challenge_method", "plain");
    assertThrows(OAuthError.class, () -> validator.validate(client, input));
  }

  @Test
  void rejectsUnsupportedParametersScopesAndPrompts() {
    for (var entry :
        Map.of(
                "scope",
                "openid admin",
                "response_type",
                "token",
                "prompt",
                "none login",
                "max_age",
                "-1",
                "response_mode",
                "fragment",
                "request_uri",
                "https://evil.example/request")
            .entrySet()) {
      var input = request();
      input.put(entry.getKey(), entry.getValue());
      assertThrows(OAuthError.class, () -> validator.validate(client, input));
    }
  }

  @Test
  void rfc7636VectorAndVerifierBoundaries() {
    assertEquals(
        "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
        BearerSecrets.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    assertThrows(OAuthError.class, () -> BearerSecrets.challenge("short"));
    assertThrows(OAuthError.class, () -> BearerSecrets.challenge("a".repeat(129)));
  }
}
