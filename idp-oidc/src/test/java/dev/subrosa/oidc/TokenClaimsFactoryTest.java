package dev.subrosa.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jwt.SignedJWT;
import dev.subrosa.domain.KeyProvider;
import dev.subrosa.domain.OAuthClient;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenClaimsFactoryTest {
  @Test
  void fixedClockAndClientLifetimeBoundAccessAndIdTokens() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var signatures =
        new JwtSignatures(
            new KeyProvider() {
              public SigningCredentials activeSigningKey() {
                return new SigningCredentials(
                    "test", (RSAPublicKey) pair.getPublic(), pair.getPrivate());
              }

              public List<PublicKey> verificationKeys() {
                return List.of(
                    new PublicKey("test", (RSAPublicKey) pair.getPublic(), "certificate"));
              }
            });
    var now = Instant.parse("2026-10-21T12:00:00Z");
    var settings =
        new OAuthSettings(
            "https://idp.example",
            Duration.ofSeconds(60),
            Duration.ofMinutes(10),
            Duration.ofHours(24),
            Duration.ofSeconds(30));
    var factory = new TokenClaimsFactory(signatures, settings, Clock.fixed(now, ZoneOffset.UTC));
    var client =
        new OAuthClient(
            "public",
            UUID.randomUUID(),
            null,
            OAuthClient.AuthMethod.NONE,
            Set.of("authorization_code"),
            Set.of("openid"),
            Set.of("https://app.example/callback"),
            Duration.ofMinutes(2),
            Duration.ofHours(1),
            null);
    var token = factory.access(client, "urn:app", "user", Set.of("openid"), Map.of(), true);
    var claims = signatures.verifySignature(token);
    assertEquals(now, claims.getIssueTime().toInstant());
    assertEquals(now.plusSeconds(120), claims.getExpirationTime().toInstant());
    assertEquals("at+jwt", SignedJWT.parse(token).getHeader().getType().toString());
    assertEquals(List.of("urn:app"), claims.getAudience());
    var id =
        signatures.verifySignature(
            factory.id(client, "user", Map.of(), now.minusSeconds(60), "nonce"));
    assertEquals(List.of("public"), id.getAudience());
    assertEquals(
        now.minusSeconds(60).getEpochSecond(), ((Number) id.getClaim("auth_time")).longValue());
    assertEquals("nonce", id.getStringClaim("nonce"));
  }

  @Test
  void globalLifetimesCannotExceedSigningRetentionCeilings() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OAuthSettings(
                "https://idp.example",
                Duration.ofSeconds(61),
                Duration.ofMinutes(10),
                Duration.ofHours(24),
                Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OAuthSettings(
                "https://idp.example",
                Duration.ofSeconds(60),
                Duration.ofMinutes(11),
                Duration.ofHours(24),
                Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OAuthSettings(
                "https://idp.example",
                Duration.ofSeconds(60),
                Duration.ofMinutes(10),
                Duration.ofHours(25),
                Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OAuthSettings(
                "https://idp.example",
                Duration.ofSeconds(60),
                Duration.ofMinutes(10),
                Duration.ofHours(24),
                Duration.ofSeconds(61)));
  }
}
