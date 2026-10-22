package dev.subrosa.oidc;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jwt.JWTClaimsSet;
import dev.subrosa.domain.OAuthClient;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Builds and signs access and ID tokens with distinct audiences and types. */
public final class TokenClaimsFactory {
  private final JwtSignatures signatures;
  private final OAuthSettings settings;
  private final Clock clock;

  /** Uses local signing primitives and the supplied issuer, lifetime policy and clock. */
  public TokenClaimsFactory(JwtSignatures signatures, OAuthSettings settings, Clock clock) {
    this.signatures = signatures;
    this.settings = settings;
    this.clock = clock;
  }

  /**
   * Signs an access token for the application's audience with an explicit user or machine grant.
   */
  public String access(
      OAuthClient client,
      String audience,
      String subject,
      Set<String> scopes,
      Map<String, Object> claims,
      boolean userGrant) {
    var builder =
        base(client, audience, subject)
            .jwtID(UUID.randomUUID().toString())
            .claim("client_id", client.clientId())
            .claim("scope", AuthorizationRequestValidator.scopeString(scopes))
            .claim("user_grant", userGrant);
    claims.forEach(builder::claim);
    return signatures.sign(builder.build(), new JOSEObjectType("at+jwt"));
  }

  /** Signs an ID token for the client with the original authentication time and optional nonce. */
  public String id(
      OAuthClient client,
      String subject,
      Map<String, Object> claims,
      Instant authTime,
      String nonce) {
    var builder =
        base(client, client.clientId(), subject).claim("auth_time", authTime.getEpochSecond());
    claims.forEach(builder::claim);
    if (nonce != null) {
      builder.claim("nonce", nonce);
    }
    return signatures.sign(builder.build(), JOSEObjectType.JWT);
  }

  private JWTClaimsSet.Builder base(OAuthClient client, String audience, String subject) {
    var now = clock.instant();
    return new JWTClaimsSet.Builder()
        .issuer(settings.issuer())
        .subject(subject)
        .audience(audience)
        .issueTime(Date.from(now))
        .expirationTime(
            Date.from(
                now.plus(OAuthSettings.shorter(client.accessTokenTtl(), settings.tokenTtl()))));
  }
}
