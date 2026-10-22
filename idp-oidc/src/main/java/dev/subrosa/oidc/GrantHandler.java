package dev.subrosa.oidc;

import dev.subrosa.domain.OAuthClient;
import java.util.Map;

/** Handles one registered OAuth token grant type. */
public interface GrantHandler {
  /** Returns the OAuth grant type handled by this implementation. */
  String grantType();

  /** Validates and exchanges the supplied grant for a token response. */
  Map<String, Object> handle(OAuthClient client, Map<String, String> form);

  /** Returns a required nonempty form value or raises a protocol error. */
  static String required(Map<String, String> form, String name) {
    var value = form.get(name);
    if (value == null || value.isEmpty()) {
      throw new OAuthError("invalid_request");
    }
    return value;
  }
}
