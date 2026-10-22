package dev.subrosa.oidc;

import com.nimbusds.jwt.SignedJWT;
import dev.subrosa.domain.AccessResolver;
import dev.subrosa.domain.ApplicationRepository;
import dev.subrosa.domain.ClaimsMapper;
import dev.subrosa.domain.ClientRepository;
import dev.subrosa.domain.UserRepository;
import java.text.ParseException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** Validates bearer-token claims and maps the current enabled user's scoped claims. */
public final class UserInfoService {
  private final JwtSignatures signatures;
  private final OAuthSettings settings;
  private final Clock clock;
  private final ClientRepository clients;
  private final ApplicationRepository applications;
  private final UserRepository users;
  private final AccessResolver access;
  private final ClaimsMapper mapper;

  /** Uses local JWT verification and domain ports to recheck the bearer subject's access. */
  public UserInfoService(
      JwtSignatures signatures,
      OAuthSettings settings,
      Clock clock,
      ClientRepository clients,
      ApplicationRepository applications,
      UserRepository users,
      AccessResolver access,
      ClaimsMapper mapper) {
    this.signatures = signatures;
    this.settings = settings;
    this.clock = clock;
    this.clients = clients;
    this.applications = applications;
    this.users = users;
    this.access = access;
    this.mapper = mapper;
  }

  /** Validates a user access token and returns the enabled subject's current scoped claims. */
  public Map<String, Object> userInfo(String token) {
    try {
      var claims = signatures.verifySignature(token);
      var header = SignedJWT.parse(token).getHeader();
      var now = clock.instant();
      if (header.getType() == null
          || !"at+jwt".equals(header.getType().toString())
          || !settings.issuer().equals(claims.getIssuer())
          || claims.getExpirationTime() == null
          || !claims.getExpirationTime().toInstant().plus(settings.clockSkew()).isAfter(now)
          || claims.getIssueTime() == null
          || claims.getIssueTime().toInstant().isAfter(now.plus(settings.clockSkew()))
          || !claims.getExpirationTime().after(claims.getIssueTime())
          || claims
              .getExpirationTime()
              .toInstant()
              .isAfter(claims.getIssueTime().toInstant().plus(settings.tokenTtl()))
          || (claims.getNotBeforeTime() != null
              && claims.getNotBeforeTime().toInstant().isAfter(now.plus(settings.clockSkew())))
          || claims.getJWTID() == null
          || claims.getJWTID().isBlank()
          || claims.getSubject() == null
          || claims.getStringClaim("client_id") == null
          || !Boolean.TRUE.equals(claims.getBooleanClaim("user_grant"))) {
        throw new IllegalArgumentException();
      }
      var client =
          clients
              .findById(claims.getStringClaim("client_id"))
              .orElseThrow(IllegalArgumentException::new);
      var app =
          applications.findById(client.applicationId()).orElseThrow(IllegalArgumentException::new);
      if (!claims.getAudience().equals(java.util.List.of(app.audience()))) {
        throw new IllegalArgumentException();
      }
      var scopes = AuthorizationRequestValidator.scopes(claims.getStringClaim("scope"));
      if (!scopes.contains("openid") || !client.scopes().containsAll(scopes)) {
        throw new IllegalArgumentException();
      }
      var subject = UUID.fromString(claims.getSubject());
      var user =
          users
              .findById(subject)
              .filter(dev.subrosa.domain.User::enabled)
              .orElseThrow(IllegalArgumentException::new);
      return mapper.map(user, access.resolve(subject, app.id()), scopes);
    } catch (ParseException | IllegalArgumentException | OAuthError exception) {
      throw new OAuthError("invalid_token");
    }
  }
}
