package dev.subrosa.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationsIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("IDP_DB_URL", POSTGRES::getJdbcUrl);
		registry.add("DB_USER", POSTGRES::getUsername);
		registry.add("DB_PASSWORD", POSTGRES::getPassword);
		registry.add("IDP_ISSUER", () -> "http://idp.localtest.me:9000");
	}

	@LocalServerPort
	int port;

	@Autowired
	Flyway flyway;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JdbcIndexedSessionRepository sessions;

	@Autowired
	Environment environment;

	@Test
	void migrationOwnsSchemaAndIsRepeatableWithoutChanges() {
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
		flyway.validate();
		assertThat(flyway.migrate().migrationsExecuted).isZero();
		assertThat(jdbc.queryForList("select tablename from pg_tables where schemaname = 'public'", String.class))
				.contains("flyway_schema_history", "spring_session", "spring_session_attributes");
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
		assertThat(environment.getProperty("spring.session.jdbc.initialize-schema")).isEqualTo("never");
	}

	@Test
	void sessionSchemaSupportsRoundTripAndCascadingDeletion() {
		verifySessionRoundTrip(sessions);
	}

	private <S extends Session> void verifySessionRoundTrip(SessionRepository<S> sessions) {
		var session = sessions.createSession();
		session.setAttribute("subject", "foundation-user");
		sessions.save(session);
		try {
			var restored = sessions.findById(session.getId());
			assertThat(restored).isNotNull();
			assertThat(restored.<String>getAttribute("subject")).isEqualTo("foundation-user");
			String primaryId = jdbc.queryForObject("select primary_id from spring_session where session_id = ?",
					String.class, session.getId());
			assertThat(
					jdbc.queryForObject("select count(*) from spring_session_attributes where session_primary_id = ?",
							Integer.class, primaryId))
					.isEqualTo(1);
			jdbc.update("delete from spring_session where primary_id = ?", primaryId);
			assertThat(
					jdbc.queryForObject("select count(*) from spring_session_attributes where session_primary_id = ?",
							Integer.class, primaryId))
					.isZero();
			assertThat(sessions.findById(session.getId())).isNull();
		} finally {
			sessions.deleteById(session.getId());
		}
	}

	@Test
	void healthIsPublicAndOtherRoutesAreDenied() throws Exception {
		try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
			var health = get(client, "/actuator/health");
			assertThat(health.statusCode()).isEqualTo(200);
			var body = new JsonMapper().readTree(health.body());
			assertThat(body.get("status").asString()).isEqualTo("UP");
			assertThat(body.has("components")).isFalse();
			assertThat(body.has("details")).isFalse();
			assertThat(get(client, "/actuator/env").statusCode()).isEqualTo(403);
			assertThat(get(client, "/admin").statusCode()).isEqualTo(403);
			assertThat(get(client, "/login").statusCode()).isEqualTo(403);
		}
	}

	private HttpResponse<String> get(HttpClient client, String path) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(10)).GET().build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
