package dev.subrosa.oidc;

import dev.subrosa.domain.OAuthClient;
import dev.subrosa.domain.OAuthGrantStore;
import java.time.Clock;
import java.util.Map;

/** Rotates refresh credentials while preventing scope expansion. */
public final class RefreshTokenGrantHandler implements GrantHandler {
  private final OAuthGrantStore store;
  private final Clock clock;
  private final TokenIssuer issuer;

  /** Uses the supplied transactional grant store, clock and token issuer. */
  public RefreshTokenGrantHandler(OAuthGrantStore store, Clock clock, TokenIssuer issuer) {
    this.store = store;
    this.clock = clock;
    this.issuer = issuer;
  }

  @Override
  public String grantType() {
    return "refresh_token";
  }

  @Override
  public Map<String, Object> handle(OAuthClient client, Map<String, String> form) {
    return store
        .rotateRefresh(
            BearerSecrets.hash(GrantHandler.required(form, "refresh_token")),
            clock.instant(),
            old -> {
              if (!old.clientId().equals(client.clientId())) {
                throw new OAuthError("invalid_grant");
              }
              var scopes =
                  form.containsKey("scope")
                      ? AuthorizationRequestValidator.scopes(form.get("scope"))
                      : old.scopes();
              if (!old.scopes().containsAll(scopes) || !client.scopes().containsAll(scopes)) {
                throw new OAuthError("invalid_scope");
              }
              return issuer.user(
                  client,
                  old.userId(),
                  scopes,
                  old.authTime(),
                  null,
                  old.familyId(),
                  old.expiresAt());
            })
        .orElseThrow(() -> new OAuthError("invalid_grant"));
  }
}
