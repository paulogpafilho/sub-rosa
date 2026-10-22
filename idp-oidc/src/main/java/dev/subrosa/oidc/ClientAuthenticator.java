package dev.subrosa.oidc;

import dev.subrosa.domain.ClientRepository;
import dev.subrosa.domain.OAuthClient;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Authenticates clients using their explicitly registered authentication method. */
public final class ClientAuthenticator {
  private final ClientRepository clients;

  /** Loads client registrations through the supplied repository port. */
  public ClientAuthenticator(ClientRepository clients) {
    this.clients = clients;
  }

  /** Authenticates a submitted client using only its registered authentication method. */
  public OAuthClient authenticate(String authorization, Map<String, String> form) {
    String id = form.get("client_id");
    String secret = form.get("client_secret");
    var method =
        secret == null ? OAuthClient.AuthMethod.NONE : OAuthClient.AuthMethod.CLIENT_SECRET_POST;
    if (authorization != null) {
      if (!authorization.regionMatches(true, 0, "Basic ", 0, 6) || secret != null) {
        throw new OAuthError("invalid_client");
      }
      try {
        var decoded =
            new String(
                Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
        int colon = decoded.indexOf(':');
        if (colon < 1) {
          throw new IllegalArgumentException();
        }
        var basicId = URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8);
        if (id != null && !id.equals(basicId)) {
          throw new IllegalArgumentException();
        }
        id = basicId;
        secret = URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8);
        method = OAuthClient.AuthMethod.CLIENT_SECRET_BASIC;
      } catch (IllegalArgumentException exception) {
        throw new OAuthError("invalid_client");
      }
    }
    var client =
        clients.findById(id == null ? "" : id).orElseThrow(() -> new OAuthError("invalid_client"));
    if (method != client.authMethod()
        || (method != OAuthClient.AuthMethod.NONE
            && !BearerSecrets.matches(client.secretHash(), BearerSecrets.hash(secret)))) {
      throw new OAuthError("invalid_client");
    }
    return client;
  }
}
