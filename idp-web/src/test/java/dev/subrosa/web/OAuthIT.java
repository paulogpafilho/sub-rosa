package dev.subrosa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.subrosa.domain.Application;
import dev.subrosa.domain.ApplicationRepository;
import dev.subrosa.domain.ClientRepository;
import dev.subrosa.domain.OAuthClient;
import dev.subrosa.domain.OAuthGrantStore;
import dev.subrosa.domain.UserRepository;
import dev.subrosa.oidc.AuthorizationRequestValidator;
import dev.subrosa.oidc.AuthorizationService;
import dev.subrosa.oidc.BearerSecrets;
import dev.subrosa.oidc.JwtSignatures;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuthIT {
  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");
  static final String REDIRECT = "http://localhost:8081/callback?fixed=1";
  static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  static final String SECRET = "a".repeat(43);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("IDP_DB_URL", POSTGRES::getJdbcUrl);
    registry.add("DB_USER", POSTGRES::getUsername);
    registry.add("DB_PASSWORD", POSTGRES::getPassword);
    registry.add("IDP_ISSUER", () -> "http://idp.localtest.me:9000");
    registry.add("IDP_KEY_ENCRYPTION_SECRET", () -> "a1".repeat(32));
    registry.add("IDP_ADMIN_USER", () -> "oauth-admin");
    registry.add("IDP_ADMIN_PASSWORD", () -> "oauth-test-password");
    registry.add("IDP_BCRYPT_COST", () -> "10");
  }

  @LocalServerPort int port;
  @Autowired ClientRepository clients;
  @Autowired ApplicationRepository applications;
  @Autowired UserRepository users;
  @Autowired AuthorizationService authorization;
  @Autowired OAuthGrantStore grants;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtSignatures signatures;
  @Autowired Clock clock;
  OAuthClient client;
  UUID user;
  final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void prepare() {
    jdbc.update("DELETE FROM applications");
    jdbc.update("UPDATE users SET enabled = true, failed_attempts = 0, locked_until = NULL");
    user = users.findByUsername("oauth-admin").orElseThrow().id();
    var app =
        applications.save(new Application(UUID.randomUUID(), "OAuth test", "urn:test", null, null));
    client =
        clients.save(
            new OAuthClient(
                "test",
                app.id(),
                null,
                OAuthClient.AuthMethod.NONE,
                Set.of("authorization_code", "refresh_token"),
                Set.of("openid", "profile", "email", "groups", "roles", "offline_access"),
                Set.of(REDIRECT),
                Duration.ofMinutes(10),
                Duration.ofHours(24),
                null));
    clients.save(
        new OAuthClient(
            "machine",
            app.id(),
            BearerSecrets.hash(SECRET),
            OAuthClient.AuthMethod.CLIENT_SECRET_BASIC,
            Set.of("client_credentials"),
            Set.of("roles"),
            Set.of(),
            Duration.ofMinutes(5),
            Duration.ofHours(1),
            null));
  }

  Map<String, String> authParams() {
    return new HashMap<>(
        Map.of(
            "client_id",
            "test",
            "response_type",
            "code",
            "redirect_uri",
            REDIRECT,
            "scope",
            "openid profile offline_access",
            "nonce",
            "nonce-value",
            "state",
            "state&value",
            "code_challenge",
            BearerSecrets.challenge(VERIFIER),
            "code_challenge_method",
            "S256"));
  }

  String code() {
    return authorization.authorize(
        client,
        new AuthorizationRequestValidator().validate(client, authParams()),
        user,
        clock.instant());
  }

  Map<String, String> exchangeForm(String code) {
    return new HashMap<>(
        Map.of(
            "grant_type",
            "authorization_code",
            "client_id",
            "test",
            "code",
            code,
            "redirect_uri",
            REDIRECT,
            "code_verifier",
            VERIFIER));
  }

  HttpResponse<String> token(Map<String, String> form) throws Exception {
    return token(form, null);
  }

  HttpResponse<String> token(Map<String, String> form, String basic) throws Exception {
    var builder =
        HttpRequest.newBuilder(uri("/oauth2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encode(form)));
    if (basic != null) {
      builder.header("Authorization", basic);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> info(String token) throws Exception {
    return http.send(
        HttpRequest.newBuilder(uri("/userinfo"))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  static Map<String, Object> json(HttpResponse<String> response) throws Exception {
    return JSONObjectUtils.parse(response.body());
  }

  URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  static String encode(Map<String, String> form) {
    return form.entrySet().stream()
        .map(
            e ->
                URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(java.util.stream.Collectors.joining("&"));
  }

  static Map<String, String> query(String location) {
    var result = new HashMap<String, String>();
    for (var part : URI.create(location).getRawQuery().split("&")) {
      var pair = part.split("=", 2);
      result.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
    }
    return result;
  }

  @Test
  void codeTokensClaimsRefreshRotationAndReuse() throws Exception {
    var rawCode = code();
    var response = token(exchangeForm(rawCode));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    var body = json(response);
    var access = (String) body.get("access_token");
    var claims = signatures.verifySignature(access);
    assertThat(SignedJWT.parse(access).getHeader().getType().toString()).isEqualTo("at+jwt");
    assertThat(claims.getAudience()).containsExactly("urn:test");
    assertThat(claims.getSubject()).isEqualTo(user.toString());
    var id = signatures.verifySignature((String) body.get("id_token"));
    assertThat(id.getAudience()).containsExactly("test");
    assertThat(id.getStringClaim("nonce")).isEqualTo("nonce-value");
    assertThat(id.getClaim("auth_time")).isNotNull();
    assertThat(info(access).statusCode()).isEqualTo(200);
    assertThat(json(info(access)))
        .containsEntry("preferred_username", "oauth-admin")
        .doesNotContainKeys("roles", "email");
    assertThat(info((String) body.get("id_token")).statusCode()).isEqualTo(401);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM authorization_codes WHERE code_hash = ?",
                Integer.class,
                rawCode))
        .isZero();
    var first = (String) body.get("refresh_token");
    var next =
        token(Map.of("grant_type", "refresh_token", "client_id", "test", "refresh_token", first));
    assertThat(next.statusCode()).isEqualTo(200);
    var second = (String) json(next).get("refresh_token");
    assertThat(second).isNotEqualTo(first);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(distinct expires_at) FROM refresh_tokens", Integer.class))
        .isEqualTo(1);
    assertThat(
            token(
                    Map.of(
                        "grant_type", "refresh_token", "client_id", "test", "refresh_token", first))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            token(
                    Map.of(
                        "grant_type",
                        "refresh_token",
                        "client_id",
                        "test",
                        "refresh_token",
                        second))
                .statusCode())
        .isEqualTo(400);
  }

  @Test
  void wrongBindingsDoNotConsumeAndCodeReplayRevokesRefresh() throws Exception {
    var code = code();
    var form = exchangeForm(code);
    form.put("redirect_uri", "http://localhost:8081/callback");
    assertThat(json(token(form))).containsEntry("error", "invalid_grant");
    form = exchangeForm(code);
    form.put("code_verifier", "b".repeat(43));
    assertThat(token(form).statusCode()).isEqualTo(400);
    var other =
        clients.save(
            new OAuthClient(
                "other",
                client.applicationId(),
                null,
                OAuthClient.AuthMethod.NONE,
                client.grantTypes(),
                client.scopes(),
                client.redirectUris(),
                client.accessTokenTtl(),
                client.refreshTokenTtl(),
                null));
    form = exchangeForm(code);
    form.put("client_id", other.clientId());
    assertThat(token(form).statusCode()).isEqualTo(400);
    var success = token(exchangeForm(code));
    assertThat(success.statusCode()).isEqualTo(200);
    assertThat(token(exchangeForm(code)).statusCode()).isEqualTo(400);
    assertThat(
            token(
                    Map.of(
                        "grant_type",
                        "refresh_token",
                        "client_id",
                        "test",
                        "refresh_token",
                        (String) json(success).get("refresh_token")))
                .statusCode())
        .isEqualTo(400);
  }

  @Test
  void expiredCodeDisabledUserMissingVerifierAndUnsupportedGrant() throws Exception {
    var expired = code();
    jdbc.update("UPDATE authorization_codes SET expires_at = now() - interval '1 second'");
    assertThat(token(exchangeForm(expired)).statusCode()).isEqualTo(400);
    var code = code();
    var form = exchangeForm(code);
    form.remove("code_verifier");
    assertThat(token(form).statusCode()).isEqualTo(400);
    jdbc.update("UPDATE users SET enabled = false WHERE id = ?", user);
    assertThat(token(exchangeForm(code)).statusCode()).isEqualTo(400);
    assertThat(json(token(Map.of("client_id", "test", "grant_type", "password"))))
        .containsEntry("error", "unsupported_grant_type");
  }

  @Test
  void concurrentCodeRedemptionAndRefreshHaveSingleWinner() throws Exception {
    var form = exchangeForm(code());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var attempts =
          executor.invokeAll(
              List.<Callable<HttpResponse<String>>>of(() -> token(form), () -> token(form)));
      assertThat(List.of(attempts.get(0).get().statusCode(), attempts.get(1).get().statusCode()))
          .containsExactlyInAnyOrder(200, 400);
      // A separate family isolates refresh concurrency from code-replay revocation.
      var first = (String) json(token(exchangeForm(code()))).get("refresh_token");
      var refresh =
          Map.of("client_id", "test", "grant_type", "refresh_token", "refresh_token", first);
      attempts =
          executor.invokeAll(
              List.<Callable<HttpResponse<String>>>of(() -> token(refresh), () -> token(refresh)));
      assertThat(List.of(attempts.get(0).get().statusCode(), attempts.get(1).get().statusCode()))
          .containsExactlyInAnyOrder(200, 400);
      var success =
          attempts.get(0).get().statusCode() == 200 ? attempts.get(0).get() : attempts.get(1).get();
      assertThat(
              token(
                      Map.of(
                          "client_id",
                          "test",
                          "grant_type",
                          "refresh_token",
                          "refresh_token",
                          (String) json(success).get("refresh_token")))
                  .statusCode())
          .isEqualTo(400);
    }
  }

  @Test
  void clientCredentialsAuthenticationAndNoUserInfo() throws Exception {
    String basic =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(("machine:" + SECRET).getBytes(StandardCharsets.UTF_8));
    var response = token(Map.of("grant_type", "client_credentials", "scope", "roles"), basic);
    assertThat(response.statusCode()).isEqualTo(200);
    var body = json(response);
    assertThat(body).doesNotContainKeys("id_token", "refresh_token");
    assertThat(signatures.verifySignature((String) body.get("access_token")).getSubject())
        .isEqualTo("machine");
    assertThat(info((String) body.get("access_token")).statusCode()).isEqualTo(401);
    var bad =
        token(
            Map.of("grant_type", "client_credentials"),
            "Basic "
                + Base64.getEncoder()
                    .encodeToString("machine:wrong".getBytes(StandardCharsets.UTF_8)));
    assertThat(bad.statusCode()).isEqualTo(401);
    assertThat(bad.headers().firstValue("WWW-Authenticate"))
        .hasValueSatisfying(value -> assertThat(value).startsWith("Basic"));
    assertThat(
            token(
                    Map.of(
                        "grant_type",
                        "client_credentials",
                        "client_id",
                        "machine",
                        "client_secret",
                        SECRET))
                .statusCode())
        .isEqualTo(400);
  }

  @Test
  void authorizationTrustBoundaryPromptNoneDuplicatesAndCsrf() throws Exception {
    var params = authParams();
    params.put("redirect_uri", "https://evil.example/callback");
    var response =
        http.send(
            HttpRequest.newBuilder(uri("/oauth2/authorize?" + encode(params))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Location")).isEmpty();
    params = authParams();
    params.put("prompt", "none");
    response =
        http.send(
            HttpRequest.newBuilder(uri("/oauth2/authorize?" + encode(params))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(query(response.headers().firstValue("Location").orElseThrow()))
        .containsEntry("error", "login_required")
        .containsEntry("state", "state&value");
    response =
        http.send(
            HttpRequest.newBuilder(
                    uri("/oauth2/authorize?" + encode(authParams()) + "&client_id=other"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Location")).isEmpty();
    response =
        http.send(
            HttpRequest.newBuilder(uri("/oauth2/authorize"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(authParams())))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void browserLoginResumesAuthorizationAndFreshLoginPrompt() throws Exception {
    var browser =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    var first =
        browser.send(
            HttpRequest.newBuilder(uri("/oauth2/authorize?" + encode(authParams()))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(first.headers().firstValue("Location")).contains("/login");
    var page =
        browser.send(
            HttpRequest.newBuilder(uri("/login")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
    assertThat(matcher.find()).isTrue();
    var login =
        browser.send(
            HttpRequest.newBuilder(uri("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        encode(
                            Map.of(
                                "username",
                                "oauth-admin",
                                "password",
                                "oauth-test-password",
                                "_csrf",
                                matcher.group(1)))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(login.headers().firstValue("Location"))
        .hasValueSatisfying(value -> assertThat(value).endsWith("/oauth2/authorize/resume"));
    var resumed =
        browser.send(
            HttpRequest.newBuilder(uri("/oauth2/authorize/resume")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    var result = query(resumed.headers().firstValue("Location").orElseThrow());
    assertThat(result)
        .containsEntry("state", "state&value")
        .containsEntry("fixed", "1")
        .containsKey("code");
    assertThat(token(exchangeForm(result.get("code"))).statusCode()).isEqualTo(200);
    var silent = authParams();
    silent.put("prompt", "none");
    silent.put("max_age", "0");
    var tooOld =
        browser.send(
            HttpRequest.newBuilder(uri("/oauth2/authorize?" + encode(silent))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(query(tooOld.headers().firstValue("Location").orElseThrow()))
        .containsEntry("error", "login_required");
    var params = authParams();
    params.put("prompt", "login");
    var fresh =
        browser.send(
            HttpRequest.newBuilder(uri("/oauth2/authorize?" + encode(params))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(fresh.headers().firstValue("Location")).contains("/login");
  }

  @Test
  void userInfoRejectsSignedWrongIssuerAudienceTimeAndType() throws Exception {
    var now = clock.instant();
    var base =
        new JWTClaimsSet.Builder()
            .issuer("http://idp.localtest.me:9000")
            .subject(user.toString())
            .audience("urn:test")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(600)))
            .jwtID(UUID.randomUUID().toString())
            .claim("client_id", "test")
            .claim("scope", "openid profile")
            .claim("user_grant", true)
            .build();
    assertThat(info(signatures.sign(base, new JOSEObjectType("at+jwt"))).statusCode())
        .isEqualTo(200);
    for (var bad :
        List.of(
            new JWTClaimsSet.Builder(base).issuer("https://evil.example").build(),
            new JWTClaimsSet.Builder(base).audience("other").build(),
            new JWTClaimsSet.Builder(base).expirationTime(Date.from(now.minusSeconds(40))).build(),
            new JWTClaimsSet.Builder(base).issueTime(Date.from(now.plusSeconds(40))).build(),
            new JWTClaimsSet.Builder(base).claim("user_grant", false).build())) {
      assertThat(info(signatures.sign(bad, new JOSEObjectType("at+jwt"))).statusCode())
          .isEqualTo(401);
    }
    assertThat(info(signatures.sign(base, JOSEObjectType.JWT)).statusCode()).isEqualTo(401);
    jdbc.update("UPDATE users SET enabled = false WHERE id = ?", user);
    assertThat(info(signatures.sign(base, new JOSEObjectType("at+jwt"))).statusCode())
        .isEqualTo(401);
  }

  @Test
  void refreshScopeCannotExpandAndRollbackPreservesToken() throws Exception {
    var first = (String) json(token(exchangeForm(code()))).get("refresh_token");
    var form =
        new HashMap<>(
            Map.of(
                "client_id",
                "test",
                "grant_type",
                "refresh_token",
                "refresh_token",
                first,
                "scope",
                "openid roles offline_access"));
    assertThat(json(token(form))).containsEntry("error", "invalid_scope");
    form.put("scope", "openid offline_access");
    var response = token(form);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json(info((String) json(response).get("access_token"))))
        .doesNotContainKey("preferred_username");
  }

  @Test
  void discoveryCorsAndRegistrationOptimisticLock() throws Exception {
    var response =
        http.send(
            HttpRequest.newBuilder(uri("/.well-known/openid-configuration")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json(response).get("response_types_supported")).isEqualTo(List.of("code"));
    var allowed =
        http.send(
            HttpRequest.newBuilder(uri("/oauth2/token"))
                .header("Origin", "http://localhost:8081")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin"))
        .contains("http://localhost:8081");
    var denied =
        http.send(
            HttpRequest.newBuilder(uri("/oauth2/token"))
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(denied.statusCode()).isEqualTo(403);
    clients.save(client);
    assertThatThrownBy(() -> clients.save(client))
        .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
  }

  @Test
  void noOfflineScopeNoRefreshAndExpiredRefreshCannotRotate() throws Exception {
    var params = authParams();
    params.put("scope", "openid profile");
    var code =
        authorization.authorize(
            client,
            new AuthorizationRequestValidator().validate(client, params),
            user,
            clock.instant());
    assertThat(json(token(exchangeForm(code)))).doesNotContainKey("refresh_token");
    var first = (String) json(token(exchangeForm(code()))).get("refresh_token");
    jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 second'");
    assertThat(
            token(
                    Map.of(
                        "client_id", "test", "grant_type", "refresh_token", "refresh_token", first))
                .statusCode())
        .isEqualTo(400);
  }

  @Test
  void missingRedirectNeverRedirectsAndTokenDuplicatesAreRejected() throws Exception {
    var params = authParams();
    params.remove("redirect_uri");
    var response =
        http.send(
            HttpRequest.newBuilder(uri("/oauth2/authorize?" + encode(params))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Location")).isEmpty();
    response =
        http.send(
            HttpRequest.newBuilder(uri("/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "client_id=test&grant_type=authorization_code&grant_type=password"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(json(response)).containsEntry("error", "invalid_request");
  }

  @Test
  void clientSecretPostIsExplicitAndCannotCombineAuthenticationMethods() throws Exception {
    clients.save(
        new OAuthClient(
            "post",
            client.applicationId(),
            BearerSecrets.hash(SECRET),
            OAuthClient.AuthMethod.CLIENT_SECRET_POST,
            Set.of("client_credentials"),
            Set.of("roles"),
            Set.of(),
            Duration.ofMinutes(5),
            Duration.ofHours(1),
            null));
    var form =
        Map.of(
            "grant_type",
            "client_credentials",
            "client_id",
            "post",
            "client_secret",
            SECRET,
            "scope",
            "roles");
    assertThat(token(form).statusCode()).isEqualTo(200);
    assertThat(
            token(
                    form,
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(("post:" + SECRET).getBytes(StandardCharsets.UTF_8)))
                .statusCode())
        .isEqualTo(401);
  }

  @Test
  void oauthSeedIsExplicitRepeatableAndNeverWidensRedirects() throws Exception {
    applications.save(
        new Application(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "Seed application",
            "urn:seed",
            null,
            null));
    POSTGRES.copyFileToContainer(
        org.testcontainers.utility.MountableFile.forHostPath(
            java.nio.file.Path.of("../deploy/seed-oauth.sql").toAbsolutePath()),
        "/tmp/seed-oauth.sql");
    var missing =
        POSTGRES.execInContainer(
            "psql",
            "-U",
            POSTGRES.getUsername(),
            "-d",
            POSTGRES.getDatabaseName(),
            "-f",
            "/tmp/seed-oauth.sql");
    assertThat(missing.getExitCode()).isNotZero();
    for (int i = 0; i < 2; i++) {
      var result =
          POSTGRES.execInContainer(
              "psql",
              "-U",
              POSTGRES.getUsername(),
              "-d",
              POSTGRES.getDatabaseName(),
              "-v",
              "seed_redirect_uri=" + REDIRECT,
              "-v",
              "seed_client_secret_hash=" + BearerSecrets.hash(SECRET),
              "-f",
              "/tmp/seed-oauth.sql");
      assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
    }
    assertThat(clients.findById("invoice-demo").orElseThrow().redirectUris())
        .containsExactly(REDIRECT);
    assertThat(clients.findById("invoice-machine").orElseThrow().secretHash())
        .isEqualTo(BearerSecrets.hash(SECRET));
    POSTGRES.execInContainer(
        "psql",
        "-U",
        POSTGRES.getUsername(),
        "-d",
        POSTGRES.getDatabaseName(),
        "-v",
        "seed_redirect_uri=https://other.example/callback",
        "-f",
        "/tmp/seed-oauth.sql");
    assertThat(clients.findById("invoice-demo").orElseThrow().redirectUris())
        .containsExactly(REDIRECT);
  }

  @Test
  void issuanceUsesRepeatableReadAndFailureRollsBackConsumption() {
    var raw = code();
    assertThatThrownBy(
            () ->
                grants.redeemCode(
                    BearerSecrets.hash(raw),
                    clock.instant(),
                    grant -> {
                      assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class))
                          .isEqualTo("repeatable read");
                      throw new IllegalStateException("simulated signing failure");
                    }))
        .hasRootCauseInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT consumed_at IS NULL FROM authorization_codes WHERE code_hash = ?",
                Boolean.class,
                BearerSecrets.hash(raw)))
        .isTrue();
  }
}
