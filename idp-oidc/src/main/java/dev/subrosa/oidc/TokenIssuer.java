package dev.subrosa.oidc;

import dev.subrosa.domain.AccessResolver;
import dev.subrosa.domain.ApplicationRepository;
import dev.subrosa.domain.ClaimsMapper;
import dev.subrosa.domain.OAuthClient;
import dev.subrosa.domain.OAuthGrantStore;
import dev.subrosa.domain.RefreshToken;
import dev.subrosa.domain.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared token assembly, called within the grant store's issuance transaction. */
public final class TokenIssuer {
  private final OAuthGrantStore store;
  private final UserRepository users;
  private final AccessResolver access;
  private final ApplicationRepository applications;
  private final ClaimsMapper mapper;
  private final TokenClaimsFactory tokens;
  private final BearerSecrets secrets;
  private final OAuthSettings settings;

  /** Uses domain ports to resolve current claims and assemble signed and opaque credentials. */
  public TokenIssuer(
      OAuthGrantStore store,
      UserRepository users,
      AccessResolver access,
      ApplicationRepository applications,
      ClaimsMapper mapper,
      TokenClaimsFactory tokens,
      BearerSecrets secrets,
      OAuthSettings settings) {
    this.store = store;
    this.users = users;
    this.access = access;
    this.applications = applications;
    this.mapper = mapper;
    this.tokens = tokens;
    this.secrets = secrets;
    this.settings = settings;
  }

  /** Issues an access token for a client subject without user grants or refresh credentials. */
  public Map<String, Object> machine(OAuthClient client, Set<String> scopes) {
    return response(
        client,
        tokens.access(client, audience(client), client.clientId(), scopes, Map.of(), false),
        scopes);
  }

  /** Resolves current user claims and issues the credentials allowed by the granted scopes. */
  public Map<String, Object> user(
      OAuthClient client,
      UUID subject,
      Set<String> scopes,
      Instant authTime,
      String nonce,
      UUID family,
      Instant refreshExpiry) {
    var user =
        users
            .findById(subject)
            .filter(dev.subrosa.domain.User::enabled)
            .orElseThrow(() -> new OAuthError("invalid_grant"));
    Map<String, Object> claims;
    try {
      claims = mapper.map(user, access.resolve(subject, client.applicationId()), scopes);
    } catch (IllegalArgumentException exception) {
      throw new OAuthError("invalid_grant");
    }
    var response =
        response(
            client,
            tokens.access(client, audience(client), subject.toString(), scopes, claims, true),
            scopes);
    if (scopes.contains("openid")) {
      response.put("id_token", tokens.id(client, subject.toString(), claims, authTime, nonce));
    }
    if (scopes.contains("offline_access") && client.grantTypes().contains("refresh_token")) {
      var raw = secrets.generate();
      store.saveRefresh(
          new RefreshToken(
              BearerSecrets.hash(raw),
              family,
              client.clientId(),
              subject,
              scopes,
              nonce,
              authTime,
              refreshExpiry));
      response.put("refresh_token", raw);
    }
    return response;
  }

  private Map<String, Object> response(OAuthClient client, String accessToken, Set<String> scopes) {
    var response = new HashMap<String, Object>();
    response.put("access_token", accessToken);
    response.put("token_type", "Bearer");
    response.put(
        "expires_in",
        OAuthSettings.shorter(client.accessTokenTtl(), settings.tokenTtl()).toSeconds());
    response.put("scope", AuthorizationRequestValidator.scopeString(scopes));
    return response;
  }

  private String audience(OAuthClient client) {
    return applications
        .findById(client.applicationId())
        .orElseThrow(() -> new OAuthError("invalid_client"))
        .audience();
  }
}
