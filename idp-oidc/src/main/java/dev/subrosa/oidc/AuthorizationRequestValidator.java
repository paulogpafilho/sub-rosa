package dev.subrosa.oidc;

import dev.subrosa.domain.OAuthClient;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates registered redirects, scopes and supported authorization parameters. */
public final class AuthorizationRequestValidator {
  /** Validated authorization parameters used for login and code issuance. */
  public record Request(
      String redirectUri,
      Set<String> scopes,
      String state,
      String nonce,
      String challenge,
      String prompt,
      Long maxAge) {}

  /** Validates an authorization request against its explicit client registration. */
  public Request validate(OAuthClient client, Map<String, String> params) {
    if (!client.grantTypes().contains("authorization_code")) {
      throw new OAuthError("unauthorized_client");
    }
    if (params.get("redirect_uri") == null
        || !client.redirectUris().contains(params.get("redirect_uri"))) {
      throw new OAuthError("invalid_request");
    }
    if (!"code".equals(params.get("response_type"))) {
      throw new OAuthError("unsupported_response_type");
    }
    if ((params.containsKey("response_mode") && !"query".equals(params.get("response_mode")))
        || params.containsKey("request")
        || params.containsKey("request_uri")
        || params.containsKey("claims")
        || params.containsKey("registration")
        || params.containsKey("id_token_hint")) {
      throw new OAuthError("invalid_request");
    }
    var scopes = scopes(params.get("scope"));
    if (scopes.isEmpty() || !client.scopes().containsAll(scopes)) {
      throw new OAuthError("invalid_scope");
    }
    if (!"S256".equals(params.get("code_challenge_method"))
        || params.get("code_challenge") == null
        || !params.get("code_challenge").matches("[A-Za-z0-9_-]{43}")) {
      throw new OAuthError("invalid_request");
    }
    var prompt = params.get("prompt");
    if (prompt != null && !Set.of("none", "login").contains(prompt)) {
      throw new OAuthError("invalid_request");
    }
    // Explicit first-party registrations have no consent UI; do not silently grant
    // offline access.
    if (scopes.contains("offline_access") && !client.grantTypes().contains("refresh_token")) {
      throw new OAuthError("invalid_scope");
    }
    Long maxAge = null;
    if (params.containsKey("max_age")) {
      try {
        maxAge = Long.valueOf(params.get("max_age"));
        if (maxAge < 0 || maxAge > 31536000) {
          throw new NumberFormatException();
        }
      } catch (NumberFormatException exception) {
        throw new OAuthError("invalid_request");
      }
    }
    return new Request(
        params.get("redirect_uri"),
        scopes,
        params.get("state"),
        params.get("nonce"),
        params.get("code_challenge"),
        prompt,
        maxAge);
  }

  /** Parses the OAuth scope syntax into a deduplicated set. */
  public static Set<String> scopes(String scope) {
    if (scope == null || scope.isEmpty()) {
      return Set.of();
    }
    if (!scope.matches("[\\x21\\x23-\\x5B\\x5D-\\x7E]+( [\\x21\\x23-\\x5B\\x5D-\\x7E]+)*")) {
      throw new OAuthError("invalid_scope");
    }
    return Set.copyOf(Arrays.asList(scope.split(" ")));
  }

  /** Serializes granted scopes in a stable order for protocol responses. */
  public static String scopeString(Set<String> scopes) {
    return scopes.stream().sorted().collect(Collectors.joining(" "));
  }
}
