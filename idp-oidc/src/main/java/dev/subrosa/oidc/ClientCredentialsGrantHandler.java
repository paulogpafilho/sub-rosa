package dev.subrosa.oidc;

import dev.subrosa.domain.OAuthClient;
import java.util.Map;

/** Issues machine tokens for authenticated confidential clients. */
public final class ClientCredentialsGrantHandler implements GrantHandler {
  private final TokenIssuer issuer;

  /** Uses the supplied issuer to assemble machine tokens. */
  public ClientCredentialsGrantHandler(TokenIssuer issuer) {
    this.issuer = issuer;
  }

  @Override
  public String grantType() {
    return "client_credentials";
  }

  @Override
  public Map<String, Object> handle(OAuthClient client, Map<String, String> form) {
    if (client.authMethod() == OAuthClient.AuthMethod.NONE) {
      throw new OAuthError("unauthorized_client");
    }
    var scopes =
        form.containsKey("scope")
            ? AuthorizationRequestValidator.scopes(form.get("scope"))
            : client.scopes();
    if (!client.scopes().containsAll(scopes)
        || scopes.contains("openid")
        || scopes.contains("offline_access")) {
      throw new OAuthError("invalid_scope");
    }
    return issuer.machine(client, scopes);
  }
}
