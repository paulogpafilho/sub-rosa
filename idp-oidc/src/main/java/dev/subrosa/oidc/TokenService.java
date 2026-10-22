package dev.subrosa.oidc;

import dev.subrosa.domain.OAuthClient;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Framework-free grant dispatch; each handler owns one protocol transition. */
public final class TokenService {
  private final Map<String, GrantHandler> handlers;

  /** Registers one handler per grant type, rejecting duplicate registrations. */
  public TokenService(List<GrantHandler> handlers) {
    this.handlers =
        handlers.stream()
            .collect(Collectors.toUnmodifiableMap(GrantHandler::grantType, Function.identity()));
  }

  /** Dispatches an allowed token grant to its registered handler. */
  public Map<String, Object> exchange(OAuthClient client, Map<String, String> form) {
    var grant = form.get("grant_type");
    if (grant == null) {
      throw new OAuthError("invalid_request");
    }
    var handler = handlers.get(grant);
    if (handler == null) {
      throw new OAuthError("unsupported_grant_type");
    }
    if (!client.grantTypes().contains(grant)) {
      throw new OAuthError("unauthorized_client");
    }
    return handler.handle(client, form);
  }
}
