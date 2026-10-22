package dev.subrosa.web;

import dev.subrosa.domain.AccessResolver;
import dev.subrosa.domain.ApplicationRepository;
import dev.subrosa.domain.ClaimsMapper;
import dev.subrosa.domain.ClientRepository;
import dev.subrosa.domain.OAuthGrantStore;
import dev.subrosa.domain.UserRepository;
import dev.subrosa.oidc.AuthorizationCodeGrantHandler;
import dev.subrosa.oidc.AuthorizationRequestValidator;
import dev.subrosa.oidc.AuthorizationService;
import dev.subrosa.oidc.BearerSecrets;
import dev.subrosa.oidc.ClientAuthenticator;
import dev.subrosa.oidc.ClientCredentialsGrantHandler;
import dev.subrosa.oidc.JwtSignatures;
import dev.subrosa.oidc.OAuthSettings;
import dev.subrosa.oidc.RefreshTokenGrantHandler;
import dev.subrosa.oidc.TokenClaimsFactory;
import dev.subrosa.oidc.TokenIssuer;
import dev.subrosa.oidc.TokenService;
import dev.subrosa.oidc.UserInfoService;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class OAuthConfiguration {
  @Bean
  OAuthSettings oauthSettings(
      IdpProperties idp,
      @Value("${IDP_CODE_TTL:PT60S}") Duration code,
      @Value("${IDP_TOKEN_TTL:PT10M}") Duration token,
      @Value("${IDP_REFRESH_TTL:PT24H}") Duration refresh,
      @Value("${IDP_CLOCK_SKEW:PT30S}") Duration skew) {
    return new OAuthSettings(idp.issuer().toString(), code, token, refresh, skew);
  }

  @Bean
  ClaimsMapper claimsMapper() {
    return new ClaimsMapper();
  }

  @Bean
  BearerSecrets bearerSecrets() {
    return new BearerSecrets(new SecureRandom());
  }

  @Bean
  AuthorizationRequestValidator authorizationValidator() {
    return new AuthorizationRequestValidator();
  }

  @Bean
  ClientAuthenticator clientAuthenticator(ClientRepository clients) {
    return new ClientAuthenticator(clients);
  }

  @Bean
  AuthorizationService authorizationService(
      OAuthGrantStore store, BearerSecrets secrets, Clock clock, OAuthSettings settings) {
    return new AuthorizationService(store, secrets, clock, settings);
  }

  @Bean
  TokenClaimsFactory tokenClaimsFactory(
      JwtSignatures signatures, OAuthSettings settings, Clock clock) {
    return new TokenClaimsFactory(signatures, settings, clock);
  }

  @Bean
  TokenService tokenService(
      OAuthGrantStore store,
      UserRepository users,
      AccessResolver access,
      ApplicationRepository apps,
      ClaimsMapper mapper,
      TokenClaimsFactory tokens,
      BearerSecrets secrets,
      Clock clock,
      OAuthSettings settings) {
    var issuer = new TokenIssuer(store, users, access, apps, mapper, tokens, secrets, settings);
    return new TokenService(
        List.of(
            new AuthorizationCodeGrantHandler(store, clock, settings, issuer),
            new ClientCredentialsGrantHandler(issuer),
            new RefreshTokenGrantHandler(store, clock, issuer)));
  }

  @Bean
  UserInfoService userInfoService(
      JwtSignatures signatures,
      OAuthSettings settings,
      Clock clock,
      ClientRepository clients,
      ApplicationRepository apps,
      UserRepository users,
      AccessResolver access,
      ClaimsMapper mapper) {
    return new UserInfoService(signatures, settings, clock, clients, apps, users, access, mapper);
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(ClientRepository clients) {
    return request -> {
      if (!Set.of("/oauth2/token", "/userinfo", "/oauth2/jwks", "/.well-known/openid-configuration")
          .contains(request.getServletPath())) {
        return null;
      }
      var config = new CorsConfiguration();
      config.setAllowedOrigins(
          clients.findAll().stream()
              .flatMap(client -> client.redirectUris().stream())
              .map(URI::create)
              .map(uri -> uri.getScheme() + "://" + uri.getRawAuthority())
              .distinct()
              .toList());
      config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
      config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
      config.setAllowCredentials(false);
      return config;
    };
  }
}
