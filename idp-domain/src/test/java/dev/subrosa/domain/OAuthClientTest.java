package dev.subrosa.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OAuthClientTest {
  @Test
  void acceptsPublicAndConfidentialRegistrations() {
    assertEquals(OAuthClient.AuthMethod.NONE, new Registration().create().authMethod());
    for (var method :
        new OAuthClient.AuthMethod[] {
          OAuthClient.AuthMethod.CLIENT_SECRET_BASIC, OAuthClient.AuthMethod.CLIENT_SECRET_POST
        }) {
      var registration = new Registration();
      registration.authMethod = method;
      registration.secretHash = "ab".repeat(32);
      registration.grants = Set.of("client_credentials");
      registration.redirects = Set.of();
      assertEquals(method, registration.create().authMethod());
    }
  }

  @Test
  void rejectsInvalidClientIdentifiers() {
    for (String id : new String[] {null, "", "contains spaces", "a".repeat(201)}) {
      var registration = new Registration();
      registration.clientId = id;
      assertThrows(IllegalArgumentException.class, registration::create);
    }
  }

  @Test
  void publicClientsCannotHaveSecretsOrUseClientCredentials() {
    var withSecret = new Registration();
    withSecret.secretHash = "ab".repeat(32);
    assertThrows(IllegalArgumentException.class, withSecret::create);

    var machine = new Registration();
    machine.grants = Set.of("client_credentials");
    assertThrows(IllegalArgumentException.class, machine::create);
  }

  @Test
  void confidentialClientsRequireLowercaseSha256Hash() {
    for (String hash : new String[] {null, "", "ab".repeat(31), "AB".repeat(32), "z".repeat(64)}) {
      var registration = new Registration();
      registration.authMethod = OAuthClient.AuthMethod.CLIENT_SECRET_BASIC;
      registration.secretHash = hash;
      assertThrows(IllegalArgumentException.class, registration::create);
    }
  }

  @Test
  void rejectsUnsupportedGrantsScopesAndInvalidGrantCombinations() {
    for (Set<String> grants :
        java.util.List.of(Set.<String>of(), Set.of("password"), Set.of("refresh_token"))) {
      var registration = new Registration();
      registration.grants = grants;
      assertThrows(IllegalArgumentException.class, registration::create);
    }
    var unsupportedScope = new Registration();
    unsupportedScope.scopes = Set.of("admin");
    assertThrows(IllegalArgumentException.class, unsupportedScope::create);

    var missingRedirect = new Registration();
    missingRedirect.redirects = Set.of();
    assertThrows(IllegalArgumentException.class, missingRedirect::create);
  }

  @Test
  void validatesEveryRegisteredRedirect() {
    for (String redirect :
        new String[] {
          "/callback",
          "https://example.test/callback#fragment",
          "https://user:password@example.test/callback",
          "ftp://example.test/callback",
          "https://example.test/" + "a".repeat(2000)
        }) {
      var registration = new Registration();
      registration.redirects = Set.of(redirect);
      assertThrows(IllegalArgumentException.class, registration::create);
    }
    var registration = new Registration();
    registration.redirects =
        Set.of("https://example.test/callback?fixed=1", "http://localhost:8081/callback");
    assertEquals(registration.redirects, registration.create().redirectUris());
  }

  @Test
  void lifetimesMustBePositiveWholeSecondsWithinTheirCeilings() {
    for (Duration duration :
        new Duration[] {
          null,
          Duration.ZERO,
          Duration.ofSeconds(-1),
          Duration.ofMillis(1500),
          Duration.ofSeconds(601)
        }) {
      var registration = new Registration();
      registration.accessTtl = duration;
      assertThrows(IllegalArgumentException.class, registration::create);
    }
    for (Duration duration :
        new Duration[] {
          null,
          Duration.ZERO,
          Duration.ofSeconds(-1),
          Duration.ofMillis(1500),
          Duration.ofSeconds(86401)
        }) {
      var registration = new Registration();
      registration.refreshTtl = duration;
      assertThrows(IllegalArgumentException.class, registration::create);
    }
    var minimum = new Registration();
    minimum.accessTtl = Duration.ofSeconds(1);
    minimum.refreshTtl = Duration.ofSeconds(1);
    assertEquals(Duration.ofSeconds(1), minimum.create().accessTokenTtl());
  }

  @Test
  void registrationCollectionsAreDefensivelyCopied() {
    var registration = new Registration();
    var grants = new HashSet<>(registration.grants);
    registration.grants = grants;
    var client = registration.create();
    grants.add("password");
    assertFalse(client.grantTypes().contains("password"));
    assertThrows(UnsupportedOperationException.class, () -> client.grantTypes().add("password"));
  }

  private static final class Registration {
    String clientId = "invoice-demo";
    String secretHash;
    OAuthClient.AuthMethod authMethod = OAuthClient.AuthMethod.NONE;
    Set<String> grants = Set.of("authorization_code", "refresh_token");
    Set<String> scopes = Set.of("openid");
    Set<String> redirects = Set.of("https://example.test/callback");
    Duration accessTtl = Duration.ofMinutes(10);
    Duration refreshTtl = Duration.ofHours(24);

    OAuthClient create() {
      return new OAuthClient(
          clientId,
          UUID.randomUUID(),
          secretHash,
          authMethod,
          grants,
          scopes,
          redirects,
          accessTtl,
          refreshTtl,
          null);
    }
  }
}
