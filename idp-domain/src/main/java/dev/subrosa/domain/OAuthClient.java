package dev.subrosa.domain;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Explicit, first-party registration. Bearer credentials are never retained. */
public record OAuthClient(
    String clientId,
    UUID applicationId,
    String secretHash,
    AuthMethod authMethod,
    Set<String> grantTypes,
    Set<String> scopes,
    Set<String> redirectUris,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    Long version) {
  public static final Set<String> SUPPORTED_SCOPES =
      Set.of("openid", "profile", "email", "groups", "roles", "offline_access");
  private static final Set<String> SUPPORTED_GRANTS =
      Set.of("authorization_code", "client_credentials", "refresh_token");
  private static final Duration MAX_ACCESS_TOKEN_TTL = Duration.ofMinutes(10);
  private static final Duration MAX_REFRESH_TOKEN_TTL = Duration.ofHours(24);

  /** Client authentication methods accepted by the token endpoint. */
  public enum AuthMethod {
    CLIENT_SECRET_BASIC,
    CLIENT_SECRET_POST,
    NONE
  }

  /**
   * Copies registration collections and validates authentication, grants, redirects and lifetimes.
   */
  public OAuthClient {
    Objects.requireNonNull(applicationId);
    Objects.requireNonNull(authMethod);
    grantTypes = Set.copyOf(grantTypes);
    scopes = Set.copyOf(scopes);
    redirectUris = Set.copyOf(redirectUris);

    validateClientId(clientId);
    validateGrants(grantTypes, redirectUris);
    validateScopes(scopes);
    validateAuthentication(authMethod, secretHash, grantTypes);
    validateLifetime(accessTokenTtl, MAX_ACCESS_TOKEN_TTL);
    validateLifetime(refreshTokenTtl, MAX_REFRESH_TOKEN_TTL);

    for (var redirectUri : redirectUris) {
      validateRedirectUri(redirectUri);
    }
  }

  private static void validateClientId(String clientId) {
    if (clientId == null) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
    if (!clientId.matches("[A-Za-z0-9._~-]{1,200}")) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
  }

  private static void validateGrants(Set<String> grantTypes, Set<String> redirectUris) {
    if (grantTypes.isEmpty()) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
    if (!SUPPORTED_GRANTS.containsAll(grantTypes)) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
    if (grantTypes.contains("refresh_token") && !grantTypes.contains("authorization_code")) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
    if (grantTypes.contains("authorization_code") && redirectUris.isEmpty()) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
  }

  private static void validateScopes(Set<String> scopes) {
    if (!SUPPORTED_SCOPES.containsAll(scopes)) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
  }

  private static void validateAuthentication(
      AuthMethod authMethod, String secretHash, Set<String> grantTypes) {
    if (authMethod == AuthMethod.NONE) {
      validatePublicClient(secretHash, grantTypes);
      return;
    }

    if (secretHash == null) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
    if (!secretHash.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
  }

  private static void validatePublicClient(String secretHash, Set<String> grantTypes) {
    if (secretHash != null) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
    if (grantTypes.contains("client_credentials")) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
  }

  private static void validateLifetime(Duration lifetime, Duration maximum) {
    if (lifetime == null) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
    if (lifetime.compareTo(Duration.ZERO) <= 0) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
    if (lifetime.compareTo(maximum) > 0) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
    if (lifetime.getNano() != 0) {
      throw new IllegalArgumentException("Invalid OAuth client registration");
    }
  }

  private static void validateRedirectUri(String redirectUri) {
    var uri = URI.create(redirectUri);
    if (redirectUri.length() > 2000) {
      throw new IllegalArgumentException("Invalid registered redirect URI");
    }
    if (uri.getHost() == null) {
      throw new IllegalArgumentException("Invalid registered redirect URI");
    }
    if (uri.getRawFragment() != null) {
      throw new IllegalArgumentException("Invalid registered redirect URI");
    }
    if (uri.getRawUserInfo() != null) {
      throw new IllegalArgumentException("Invalid registered redirect URI");
    }
    var scheme = uri.getScheme();
    if (!"https".equals(scheme) && !"http".equals(scheme)) {
      throw new IllegalArgumentException("Invalid registered redirect URI");
    }
  }

  @Override
  public String toString() {
    return "OAuthClient[clientId=" + clientId + "]";
  }
}
