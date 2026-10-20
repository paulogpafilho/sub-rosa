package dev.subrosa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import javax.sql.DataSource;

import dev.subrosa.domain.AuthenticationService;
import dev.subrosa.domain.PasswordHasher;
import dev.subrosa.domain.User;
import dev.subrosa.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ActiveProfiles("dev")
@Import(IdentityIT.TimeConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityIT {
	private static final String PASSWORD = "integration-password";
	private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("IDP_KEY_ENCRYPTION_SECRET", () -> "a1".repeat(32));
		registry.add("IDP_DB_URL", POSTGRES::getJdbcUrl);
		registry.add("DB_USER", POSTGRES::getUsername);
		registry.add("DB_PASSWORD", POSTGRES::getPassword);
		registry.add("IDP_ISSUER", () -> "http://idp.localtest.me:9000");
		registry.add("IDP_ADMIN_USER", () -> "admin");
		registry.add("IDP_ADMIN_PASSWORD", () -> PASSWORD);
		registry.add("IDP_BCRYPT_COST", () -> "10");
		registry.add("IDP_IP_ATTEMPTS", () -> "200");
	}

	@LocalServerPort
	int port;
	@Autowired
	UserRepository users;
	@Autowired
	PasswordHasher passwords;
	@Autowired
	AuthenticationService authentication;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	DataSource dataSource;
	@Autowired
	JdbcIndexedSessionRepository sessions;
	@Autowired
	MutableClock clock;
	@Autowired
	@Qualifier("bootstrapAdministrator")
	ApplicationRunner bootstrap;

	@BeforeEach
	void reset() throws Exception {
		clock.now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		jdbc.update("DELETE FROM users WHERE username <> 'admin'");
		jdbc.update("UPDATE users SET enabled = true, failed_attempts = 0, locked_until = NULL");
		bootstrap.run(new DefaultApplicationArguments());
	}

	@Test
	void bootstrapStoresOnlyHashAndDoesNotReplaceAnExistingAccount() throws Exception {
		var original = users.findByUsername("admin").orElseThrow();
		assertThat(passwords.matches(PASSWORD, original.passwordHash())).isTrue();
		assertThat(original.passwordHash()).doesNotContain(PASSWORD);
		assertThat(original.toString()).doesNotContain(original.passwordHash(), PASSWORD);
		bootstrap.run(new DefaultApplicationArguments());
		assertThat(users.findByUsername("admin").orElseThrow().id()).isEqualTo(original.id());
		assertThat(users.createFirst(
				new User(UUID.randomUUID(), "second", null, original.passwordHash(), true, 0, null, clock.instant())))
				.isFalse();
		assertThat(users.findByUsername("second")).isEmpty();
	}

	@Test
	void bootstrapSerializesConcurrentFirstStartsWithDifferentUsernames() throws Exception {
		String hash = users.findByUsername("admin").orElseThrow().passwordHash();
		jdbc.update("DELETE FROM users");
		try (var executor = Executors.newFixedThreadPool(2)) {
			var tasks = IntStream.range(0, 2)
					.<Callable<Boolean>>mapToObj(index -> () -> users.createFirst(
							new User(UUID.randomUUID(), "first-" + index, null, hash, true, 0, null, clock.instant())))
					.toList();
			int inserted = 0;
			for (var result : executor.invokeAll(tasks)) {
				if (result.get()) {
					inserted++;
				}
			}
			assertThat(inserted).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
		} finally {
			jdbc.update("DELETE FROM users");
			bootstrap.run(new DefaultApplicationArguments());
		}
	}

	@Test
	void loginRotatesSessionSharesItAcrossBrowsersAndLogoutDestroysIt() throws Exception {
		try (var browser = new Browser(); var secondTab = new Browser()) {
			var loginPage = browser.get("/login");
			String oldId = browser.sessionId();
			assertThat(browser.post("/login", "username=admin&password=" + PASSWORD).statusCode()).isEqualTo(403);
			var login = browser.login("admin", PASSWORD, token(loginPage));
			assertThat(login.statusCode()).isEqualTo(302);
			assertThat(login.headers().firstValue("location")).hasValue(base() + "/");
			String id = browser.sessionId();
			assertThat(id).isNotEqualTo(oldId);
			assertThat(sessions.findById(oldId)).isNull();
			assertThat(login.headers().allValues("set-cookie").toString()).contains("HttpOnly", "SameSite=Lax")
					.doesNotContain("Secure");
			assertThat(browser.get("/").body()).contains("You are signed in");
			secondTab.cookies.getCookieStore().add(URI.create(base()), browser.sessionCookie());
			assertThat(secondTab.get("/").statusCode()).isEqualTo(200);

			// An independent repository instance can deserialize the same
			// authentication/session.
			SessionRepository<?> otherRepository = new JdbcIndexedSessionRepository(jdbc,
					new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
			var shared = otherRepository.findById(id);
			assertThat(shared).isNotNull();
			assertThat(shared.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
			assertThat(shared.<Long>getAttribute(AbsoluteSessionTimeoutFilter.AUTHENTICATED_AT))
					.isEqualTo(clock.millis());
			assertThat(shared.<Object>getAttribute("SPRING_SECURITY_CONTEXT").toString()).doesNotContain(PASSWORD,
					"$2a$");

			assertThat(browser.get("/logout").statusCode()).isEqualTo(403);
			assertThat(browser.post("/logout", "").statusCode()).isEqualTo(403);
			var home = browser.get("/");
			assertThat(browser.post("/logout", "_csrf=" + encode(token(home))).statusCode()).isEqualTo(302);
			assertThat(sessions.findById(id)).isNull();
			assertThat(secondTab.get("/").statusCode()).isEqualTo(302);
		}
	}

	@Test
	void absoluteExpiryCannotBeExtendedByActivity() throws Exception {
		try (var browser = new Browser()) {
			assertThat(browser.login("admin", PASSWORD).statusCode()).isEqualTo(302);
			String id = browser.sessionId();
			clock.now = clock.now.plus(Duration.ofHours(8)).minusMillis(1);
			assertThat(browser.get("/").statusCode()).isEqualTo(200);
			clock.now = clock.now.plusMillis(1);
			assertThat(browser.get("/").statusCode()).isEqualTo(302);
			assertThat(sessions.findById(id)).isNull();
		}
	}

	@Test
	void idleExpiryRejectsPersistedSession() throws Exception {
		try (var browser = new Browser()) {
			browser.login("admin", PASSWORD);
			String id = browser.sessionId();
			// Move the persisted last-access/expiry into the past without a slow wall-clock
			// sleep.
			jdbc.update("UPDATE spring_session SET last_access_time = ?, expiry_time = ? WHERE session_id = ?",
					System.currentTimeMillis() - Duration.ofHours(1).toMillis(), System.currentTimeMillis() - 1, id);
			assertThat(browser.get("/").statusCode()).isEqualTo(302);
		}
	}

	@Test
	void unknownDisabledLockedAndOversizedPasswordsHaveTheSamePublicFailure() throws Exception {
		try (var browser = new Browser()) {
			for (String username : new String[]{"missing", "admin"}) {
				var response = browser.login(username, "wrong");
				assertThat(response.statusCode()).isEqualTo(302);
				assertThat(response.headers().firstValue("location")).hasValue(base() + "/login?error");
			}
			assertThat(browser.login("admin", "é".repeat(37)).headers().firstValue("location"))
					.hasValue(base() + "/login?error");
			jdbc.update("UPDATE users SET enabled = false");
			assertThat(browser.login("admin", PASSWORD).headers().firstValue("location"))
					.hasValue(base() + "/login?error");
			assertThat(browser.get("/login?error").body()).contains("Invalid credentials").doesNotContain("disabled",
					"locked");
		}
	}

	@Test
	void fiveFailuresLockTheAccountAndItRecoversAtTheExactExpiry() {
		for (int index = 0; index < 5; index++) {
			assertThat(authentication.authenticate("admin", "wrong")).isEmpty();
		}
		var locked = users.findByUsername("admin").orElseThrow();
		assertThat(locked.failedAttempts()).isEqualTo(5);
		assertThat(locked.lockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
		assertThat(authentication.authenticate("admin", PASSWORD)).isEmpty();
		assertThat(authentication.authenticate("admin", "wrong")).isEmpty();
		assertThat(users.findByUsername("admin").orElseThrow().lockedUntil()).isEqualTo(locked.lockedUntil());
		clock.now = locked.lockedUntil();
		assertThat(authentication.authenticate("admin", PASSWORD)).isPresent();
		assertThat(users.findByUsername("admin").orElseThrow().failedAttempts()).isZero();
		assertThat(users.findByUsername("admin").orElseThrow().lockedUntil()).isNull();
	}

	@Test
	void concurrentFailuresDoNotLoseIncrementsAndStaleSuccessCannotClearTheLock() throws Exception {
		var before = users.findByUsername("admin").orElseThrow();
		try (var executor = Executors.newFixedThreadPool(8)) {
			var tasks = IntStream.range(0, 20).<Callable<Void>>mapToObj(index -> () -> {
				users.recordFailure(before.id(), clock.instant(), 5, Duration.ofMinutes(15));
				return null;
			}).toList();
			for (var result : executor.invokeAll(tasks)) {
				result.get();
			}
		}
		assertThat(users.findByUsername("admin").orElseThrow().failedAttempts()).isEqualTo(5);
		assertThat(users.recordSuccess(before, clock.instant())).isFalse();
		clock.now = clock.now.plus(Duration.ofMinutes(15));
		assertThat(authentication.authenticate("admin", "wrong")).isEmpty();
		assertThat(users.findByUsername("admin").orElseThrow().failedAttempts()).isEqualTo(1);
	}

	@Test
	void bootstrapFailsClosedWithoutCredentialsOnlyForAnEmptyDatabase() throws Exception {
		var settings = new IdentityProperties(10, 72, 5, Duration.ofMinutes(15), 20, Duration.ofMinutes(1), 100,
				Duration.ofMinutes(30), Duration.ofHours(8), "", "");
		var runner = new IdentityConfiguration().bootstrapAdministrator(users, passwords, clock, settings);
		runner.run(new DefaultApplicationArguments());
		jdbc.update("DELETE FROM users");
		try {
			assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("requires IDP_ADMIN_USER");
		} finally {
			bootstrap.run(new DefaultApplicationArguments());
		}
	}

	private String base() {
		return "http://localhost:" + port;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String token(HttpResponse<String> page) {
		var matcher = CSRF.matcher(page.body());
		assertThat(matcher.find()).as("CSRF hidden input in rendered form").isTrue();
		return matcher.group(1);
	}

	private final class Browser implements AutoCloseable {
		final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10))
				.build();

		HttpResponse<String> get(String path) throws Exception {
			return send(HttpRequest.newBuilder(URI.create(base() + path)).GET());
		}

		HttpResponse<String> post(String path, String body) throws Exception {
			return send(HttpRequest.newBuilder(URI.create(base() + path))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)));
		}

		HttpResponse<String> login(String username, String password) throws Exception {
			return login(username, password, token(get("/login")));
		}

		HttpResponse<String> login(String username, String password, String csrf) throws Exception {
			return post("/login",
					"username=" + encode(username) + "&password=" + encode(password) + "&_csrf=" + encode(csrf));
		}

		java.net.HttpCookie sessionCookie() {
			return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("SESSION"))
					.findFirst().orElseThrow();
		}

		String sessionId() {
			return new String(Base64.getDecoder().decode(sessionCookie().getValue()), StandardCharsets.UTF_8);
		}

		private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
			return client.send(request.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
		}

		@Override
		public void close() {
			client.close();
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TimeConfiguration {
		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}
	}

	static final class MutableClock extends Clock {
		volatile Instant now = Instant.now();

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
