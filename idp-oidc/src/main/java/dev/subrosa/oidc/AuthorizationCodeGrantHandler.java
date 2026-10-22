package dev.subrosa.oidc;

import dev.subrosa.domain.AuthorizationCode;
import dev.subrosa.domain.OAuthClient;
import dev.subrosa.domain.OAuthGrantStore;
import java.time.Clock;
import java.util.Map;

/** Redeems client-bound authorization codes using S256 PKCE. */
public final class AuthorizationCodeGrantHandler implements GrantHandler {
  private final OAuthGrantStore store;
  private final Clock clock;
  private final OAuthSettings settings;
  private final TokenIssuer issuer;

  /** Uses the transactional grant store, token issuer and configured clock and lifetimes. */
  public AuthorizationCodeGrantHandler(
      OAuthGrantStore store, Clock clock, OAuthSettings settings, TokenIssuer issuer) {
    this.store = store;
    this.clock = clock;
    this.settings = settings;
    this.issuer = issuer;
  }

  @Override
  public String grantType() {
    return "authorization_code";
  }

  @Override
  public Map<String, Object> handle(OAuthClient client, Map<String, String> form) {
    var verifier = GrantHandler.required(form, "code_verifier");
    var challenge = BearerSecrets.challenge(verifier);
    var redirectUri = GrantHandler.required(form, "redirect_uri");
    var authorizationCode = GrantHandler.required(form, "code");
    var codeHash = BearerSecrets.hash(authorizationCode);

    var result =
        store.redeemCode(
            codeHash,
            clock.instant(),
            code -> validateAndIssueTokens(client, code, redirectUri, challenge));

    if (result.isEmpty()) {
      throw new OAuthError("invalid_grant");
    }
    return result.get();
  }

  private Map<String, Object> validateAndIssueTokens(
      OAuthClient client, AuthorizationCode code, String redirectUri, String challenge) {
    validateGrant(client, code, redirectUri, challenge);
    return issueTokens(client, code);
  }

  private void validateGrant(
      OAuthClient client, AuthorizationCode code, String redirectUri, String challenge) {
    if (!code.clientId().equals(client.clientId())) {
      throw new OAuthError("invalid_grant");
    }
    if (!code.redirectUri().equals(redirectUri)) {
      throw new OAuthError("invalid_grant");
    }
    if (!BearerSecrets.matches(code.challenge(), challenge)) {
      throw new OAuthError("invalid_grant");
    }
    if (!client.scopes().containsAll(code.scopes())) {
      throw new OAuthError("invalid_grant");
    }
  }

  private Map<String, Object> issueTokens(OAuthClient client, AuthorizationCode code) {
    var refreshLifetime = OAuthSettings.shorter(client.refreshTokenTtl(), settings.refreshTtl());
    var refreshExpiresAt = clock.instant().plus(refreshLifetime);

    return issuer.user(
        client,
        code.userId(),
        code.scopes(),
        code.authTime(),
        code.nonce(),
        code.familyId(),
        refreshExpiresAt);
  }
}
