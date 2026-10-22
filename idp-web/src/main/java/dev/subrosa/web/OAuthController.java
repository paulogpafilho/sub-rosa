package dev.subrosa.web;

import dev.subrosa.domain.AccessResolver;
import dev.subrosa.domain.ClientRepository;
import dev.subrosa.oidc.AuthorizationRequestValidator;
import dev.subrosa.oidc.AuthorizationService;
import dev.subrosa.oidc.ClientAuthenticator;
import dev.subrosa.oidc.OAuthError;
import dev.subrosa.oidc.OAuthSettings;
import dev.subrosa.oidc.TokenService;
import dev.subrosa.oidc.UserInfoService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class OAuthController {
  static final String PENDING = "idp.oauth.pending";
  private final ClientRepository clients;
  private final AuthorizationRequestValidator validator;
  private final AuthorizationService authorization;
  private final ClientAuthenticator authenticator;
  private final TokenService tokens;
  private final UserInfoService userInfo;
  private final AccessResolver access;
  private final OAuthSettings settings;
  private final Clock clock;

  OAuthController(
      ClientRepository clients,
      AuthorizationRequestValidator validator,
      AuthorizationService authorization,
      ClientAuthenticator authenticator,
      TokenService tokens,
      UserInfoService userInfo,
      AccessResolver access,
      OAuthSettings settings,
      Clock clock) {
    this.clients = clients;
    this.validator = validator;
    this.authorization = authorization;
    this.authenticator = authenticator;
    this.tokens = tokens;
    this.userInfo = userInfo;
    this.access = access;
    this.settings = settings;
    this.clock = clock;
  }

  @RequestMapping(
      value = "/oauth2/authorize",
      method = {RequestMethod.GET, RequestMethod.POST})
  ResponseEntity<?> authorize(HttpServletRequest request, Authentication authentication) {
    try {
      return authorize(parameters(request), request, authentication, false);
    } catch (OAuthError error) {
      return localError();
    }
  }

  private ResponseEntity<?> authorize(
      Map<String, String> params,
      HttpServletRequest request,
      Authentication authentication,
      boolean resumed) {
    var client = clients.findById(params.getOrDefault("client_id", "")).orElse(null);
    // Establish a trusted redirect before returning ANY protocol error to it.
    if (client == null
        || params.get("redirect_uri") == null
        || !client.redirectUris().contains(params.get("redirect_uri"))) {
      return localError();
    }
    try {
      var validated = validator.validate(client, params);
      var session = request.getSession(false);
      Instant authTime =
          session != null
                  && session.getAttribute(AbsoluteSessionTimeoutFilter.AUTHENTICATED_AT)
                      instanceof Long timestamp
              ? Instant.ofEpochMilli(timestamp)
              : null;
      boolean login =
          authentication == null
              || authTime == null
              || (!resumed
                  && ("login".equals(validated.prompt())
                      || (validated.maxAge() != null
                          && !clock.instant().isBefore(authTime.plusSeconds(validated.maxAge())))));
      if (login) {
        if ("none".equals(validated.prompt())) {
          throw new OAuthError("login_required");
        }
        if (session != null) {
          session.invalidate();
        }
        SecurityContextHolder.clearContext();
        request.getSession().setAttribute(PENDING, new HashMap<>(params));
        return redirect("/login");
      }
      var subject = UUID.fromString(authentication.getName());
      try {
        access.resolve(subject, client.applicationId());
      } catch (IllegalArgumentException exception) {
        throw new OAuthError("access_denied");
      }
      var code = authorization.authorize(client, validated, subject, authTime);
      return redirect(callback(validated.redirectUri(), Map.of("code", code), validated.state()));
    } catch (OAuthError error) {
      return redirect(
          callback(
              params.get("redirect_uri"),
              Map.of("error", error.code(), "error_description", error.code()),
              params.get("state")));
    }
  }

  @GetMapping("/oauth2/authorize/resume")
  ResponseEntity<?> resume(HttpServletRequest request, Authentication authentication) {
    var session = request.getSession(false);
    if (session == null || !(session.getAttribute(PENDING) instanceof Map<?, ?> pending)) {
      return localError();
    }
    session.removeAttribute(PENDING);
    var params = new HashMap<String, String>();
    pending.forEach((key, value) -> params.put((String) key, (String) value));
    return authorize(params, request, authentication, true);
  }

  @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @ResponseBody
  ResponseEntity<?> token(HttpServletRequest request) {
    try {
      if (request.getQueryString() != null) {
        throw new OAuthError("invalid_request");
      }
      var headers = java.util.Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
      if (headers.size() > 1) {
        throw new OAuthError("invalid_client");
      }
      var form = parameters(request);
      var client = authenticator.authenticate(headers.isEmpty() ? null : headers.getFirst(), form);
      return noStore(200).body(tokens.exchange(client, form));
    } catch (OAuthError error) {
      boolean basic =
          request.getHeader(HttpHeaders.AUTHORIZATION) != null
              && request
                  .getHeader(HttpHeaders.AUTHORIZATION)
                  .regionMatches(true, 0, "Basic ", 0, 6);
      var response = noStore("invalid_client".equals(error.code()) && basic ? 401 : 400);
      if ("invalid_client".equals(error.code()) && basic) {
        response.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"oauth2/token\"");
      }
      return response.body(Map.of("error", error.code()));
    }
  }

  @RequestMapping(
      value = "/userinfo",
      method = {RequestMethod.GET, RequestMethod.POST})
  @ResponseBody
  ResponseEntity<?> userInfo(HttpServletRequest request) {
    try {
      var headers = java.util.Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
      if (headers.size() != 1
          || !headers.getFirst().regionMatches(true, 0, "Bearer ", 0, 7)
          || request.getParameter("access_token") != null) {
        throw new OAuthError("invalid_token");
      }
      return noStore(200).body(userInfo.userInfo(headers.getFirst().substring(7)));
    } catch (OAuthError error) {
      return noStore(401)
          .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
          .body(Map.of("error", "invalid_token"));
    }
  }

  @GetMapping("/.well-known/openid-configuration")
  @ResponseBody
  Map<String, Object> discovery() {
    var issuer = settings.issuer();
    return Map.ofEntries(
        Map.entry("issuer", issuer),
        Map.entry("authorization_endpoint", issuer + "/oauth2/authorize"),
        Map.entry("token_endpoint", issuer + "/oauth2/token"),
        Map.entry("userinfo_endpoint", issuer + "/userinfo"),
        Map.entry("jwks_uri", issuer + "/oauth2/jwks"),
        Map.entry("response_types_supported", java.util.List.of("code")),
        Map.entry("response_modes_supported", java.util.List.of("query")),
        Map.entry("subject_types_supported", java.util.List.of("public")),
        Map.entry("id_token_signing_alg_values_supported", java.util.List.of("RS256")),
        Map.entry(
            "grant_types_supported",
            java.util.List.of("authorization_code", "client_credentials", "refresh_token")),
        Map.entry(
            "token_endpoint_auth_methods_supported",
            java.util.List.of("client_secret_basic", "client_secret_post", "none")),
        Map.entry(
            "scopes_supported",
            dev.subrosa.domain.OAuthClient.SUPPORTED_SCOPES.stream().sorted().toList()),
        Map.entry("code_challenge_methods_supported", java.util.List.of("S256")),
        Map.entry("authorization_response_iss_parameter_supported", true),
        Map.entry("request_parameter_supported", false),
        Map.entry("request_uri_parameter_supported", false),
        Map.entry("claims_parameter_supported", false));
  }

  private String callback(String uri, Map<String, String> values, String state) {
    var result = new StringBuilder(uri).append(uri.contains("?") ? "&" : "?");
    values.forEach(
        (key, value) -> result.append(key).append('=').append(encode(value)).append('&'));
    if (state != null) {
      result.append("state=").append(encode(state)).append('&');
    }
    return result.append("iss=").append(encode(settings.issuer())).toString();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static ResponseEntity.BodyBuilder noStore(int status) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.PRAGMA, "no-cache");
  }

  private static ResponseEntity<?> redirect(String uri) {
    return noStore(302)
        .header(HttpHeaders.LOCATION, uri)
        .header("Referrer-Policy", "no-referrer")
        .build();
  }

  private static ResponseEntity<?> localError() {
    return noStore(400)
        .contentType(MediaType.TEXT_HTML)
        .body(
            "<!doctype html><title>Authorization error</title>"
                + "<h1>Invalid authorization request</h1>");
  }

  private static Map<String, String> parameters(HttpServletRequest request) {
    var result = new HashMap<String, String>();
    if (request.getParameterMap().size() > 32) {
      throw new OAuthError("invalid_request");
    }
    request
        .getParameterMap()
        .forEach(
            (name, values) -> {
              if (values.length != 1 || name.length() > 200 || values[0].length() > 2000) {
                throw new OAuthError("invalid_request");
              }
              result.put(name, values[0]);
            });
    return result;
  }
}
