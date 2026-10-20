package dev.subrosa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import dev.subrosa.domain.SigningKeyRepository;
import dev.subrosa.oidc.JwtSignatures;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
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
class SigningKeysIT {
	private static final String SECRET = "a1".repeat(32);
	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("IDP_DB_URL", POSTGRES::getJdbcUrl);
		registry.add("DB_USER", POSTGRES::getUsername);
		registry.add("DB_PASSWORD", POSTGRES::getPassword);
		registry.add("IDP_ISSUER", () -> "http://idp.localtest.me:9000");
		registry.add("IDP_ADMIN_USER", () -> "keys-admin");
		registry.add("IDP_ADMIN_PASSWORD", () -> "test-key-password");
		registry.add("IDP_BCRYPT_COST", () -> "10");
		registry.add("IDP_KEY_ENCRYPTION_SECRET", () -> SECRET);
	}

	@Autowired
	SigningKeyRepository repository;
	@Autowired
	StoredKeyProvider liveKeys;
	@Autowired
	JdbcTemplate jdbc;
	@LocalServerPort
	int port;
	final SigningKeyCodec codec = new SigningKeyCodec(new PrivateKeyEncryption(SECRET));

	@BeforeEach
	void resetKeys() {
		jdbc.update("delete from signing_keys");
		liveKeys.initialize();
	}

	@Test
	void storesEncryptedKeyAndMatchingSelfSignedCertificateAndSurvivesRestart() throws Exception {
		var stored = repository.active().orElseThrow();
		assertThat(stored.privateKeyEncrypted()).startsWith("v1:").doesNotContain("PRIVATE KEY");
		var credentials = liveKeys.activeSigningKey();
		assertThat(stored.kid()).isEqualTo(java.util.HexFormat.of().formatHex(
				java.security.MessageDigest.getInstance("SHA-256").digest(credentials.publicKey().getEncoded())));
		assertThat(stored.privateKeyEncrypted())
				.doesNotContain(java.util.Base64.getEncoder().encodeToString(credentials.privateKey().getEncoded()));
		var cert = (java.security.cert.X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
				new ByteArrayInputStream(stored.certificatePem().getBytes(StandardCharsets.US_ASCII)));
		cert.verify(credentials.publicKey());
		cert.checkValidity();
		assertThat(cert.getPublicKey()).isEqualTo(credentials.publicKey());
		assertThat(cert.getBasicConstraints()).isEqualTo(-1);
		var restarted = new StoredKeyProvider(repository, codec, Clock.systemUTC());
		restarted.initialize();
		assertThat(restarted.activeSigningKey().kid()).isEqualTo(stored.kid());
		assertThatThrownBy(() -> new StoredKeyProvider(repository,
				new SigningKeyCodec(new PrivateKeyEncryption("b2".repeat(32))), Clock.systemUTC()).initialize())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Unable to decrypt");
		assertThat(repository.active().orElseThrow()).isEqualTo(stored);
	}

	@Test
	void concurrentFirstStartsGenerateExactlyOnceAndDatabaseRejectsSecondActiveKey() throws Exception {
		jdbc.update("delete from signing_keys");
		var generated = new AtomicInteger();
		try (var executor = Executors.newFixedThreadPool(8)) {
			var tasks = IntStream.range(0, 8).mapToObj(index -> (Callable<String>) () -> repository.initialize(() -> {
				generated.incrementAndGet();
				return codec.generate(Instant.now());
			}).kid()).toList();
			var results = executor.invokeAll(tasks);
			var kid = results.getFirst().get();
			for (var result : results) {
				assertThat(result.get()).isEqualTo(kid);
			}
		}
		assertThat(generated.get()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from signing_keys", Integer.class)).isEqualTo(1);
		assertThatThrownBy(() -> jdbc.update(
				"insert into signing_keys select 'duplicate', private_key_enc, public_pem, cert_pem, status, created_at, retire_at from signing_keys"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rotationPreservesOldSignaturesUntilDeadlineAndRollsBackOnFailure() {
		var clock = new MutableClock();
		var provider = new StoredKeyProvider(repository, codec, clock);
		var signatures = new JwtSignatures(provider);
		String oldKid = provider.activeSigningKey().kid();
		String oldToken = signatures.sign(new JWTClaimsSet.Builder().subject("user").build(), JOSEObjectType.JWT);
		assertThatThrownBy(() -> provider.rotate(Duration.ofMinutes(10))).isInstanceOf(IllegalArgumentException.class);
		provider.rotate(Duration.ofHours(24));
		assertThat(provider.activeSigningKey().kid()).isNotEqualTo(oldKid);
		assertThat(provider.verificationKeys()).hasSize(2);
		assertThat(signatures.verifySignature(oldToken).getSubject()).isEqualTo("user");
		String newToken = signatures.sign(new JWTClaimsSet.Builder().subject("new").build(), JOSEObjectType.JWT);
		String newKid = provider.activeSigningKey().kid();
		assertThatThrownBy(() -> repository.rotate(() -> {
			throw new IllegalStateException("generation failed");
		}, clock.instant(), clock.instant().plusSeconds(86400)))
				.isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThat(provider.activeSigningKey().kid()).isEqualTo(newKid);
		var duplicate = repository.active().orElseThrow();
		assertThatThrownBy(
				() -> repository.rotate(() -> duplicate, clock.instant(), clock.instant().plusSeconds(86400)))
				.isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
		assertThat(provider.activeSigningKey().kid()).isEqualTo(newKid);
		clock.now = clock.now.plus(Duration.ofHours(24)).minusNanos(1000);
		assertThat(signatures.verifySignature(oldToken).getSubject()).isEqualTo("user");
		clock.now = clock.now.plusNanos(1000);
		assertThat(provider.verificationKeys()).hasSize(1);
		assertThatThrownBy(() -> signatures.verifySignature(oldToken)).isInstanceOf(IllegalArgumentException.class);
		assertThat(signatures.verifySignature(newToken).getSubject()).isEqualTo("new");
		assertThat(jdbc.queryForObject("select count(*) from signing_keys", Integer.class)).isEqualTo(2);
	}

	@Test
	void jwksIsPublicContainsOnlyPublicFieldsAndVerifiesSignature() throws Exception {
		var signatures = new JwtSignatures(liveKeys);
		try (var client = HttpClient.newHttpClient()) {
			var response = client.send(
					HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/jwks")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
			var jwk = JWKSet.parse(response.body()).getKeys().getFirst().toRSAKey();
			assertThat(jwk.isPrivate()).isFalse();
			assertThat(jwk.toJSONObject().keySet()).containsExactlyInAnyOrder("kty", "kid", "alg", "use", "n", "e");
			var jwt = SignedJWT.parse(
					signatures.sign(new JWTClaimsSet.Builder().subject("public-check").build(), JOSEObjectType.JWT));
			assertThat(jwt.verify(new RSASSAVerifier(jwk))).isTrue();
		}
	}

	static class MutableClock extends Clock {
		Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
		public Instant instant() {
			return now;
		}
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
