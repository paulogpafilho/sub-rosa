package dev.subrosa.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import dev.subrosa.domain.AccessResolver;
import dev.subrosa.domain.Application;
import dev.subrosa.domain.ApplicationRepository;
import dev.subrosa.domain.Entitlement;
import dev.subrosa.domain.Group;
import dev.subrosa.domain.GroupRepository;
import dev.subrosa.domain.Role;
import dev.subrosa.domain.RoleRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AccessModelIT {
	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("IDP_DB_URL", POSTGRES::getJdbcUrl);
		registry.add("IDP_KEY_ENCRYPTION_SECRET", () -> "a1".repeat(32));
		registry.add("DB_USER", POSTGRES::getUsername);
		registry.add("DB_PASSWORD", POSTGRES::getPassword);
		registry.add("IDP_ISSUER", () -> "http://idp.localtest.me:9000");
		registry.add("IDP_ADMIN_USER", () -> "access-admin");
		registry.add("IDP_ADMIN_PASSWORD", () -> "test-access-password");
		registry.add("IDP_BCRYPT_COST", () -> "10");
	}

	@Autowired
	AccessResolver resolver;
	@Autowired
	ApplicationRepository applications;
	@Autowired
	GroupRepository groups;
	@Autowired
	RoleRepository roles;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	EntityManagerFactory entityManagerFactory;

	UUID user;
	Application app;
	Group finance;
	Role reader;
	Role approver;

	@BeforeEach
	void prepare() {
		jdbc.update("DELETE FROM applications");
		jdbc.update("DELETE FROM groups");
		jdbc.update("UPDATE users SET enabled = true WHERE username = 'access-admin'");
		user = jdbc.queryForObject("SELECT id FROM users WHERE username = 'access-admin'", UUID.class);
		app = applications.save(new Application(UUID.randomUUID(), "Invoices", "urn:invoices", "Demo", null));
		finance = groups.save(new Group(UUID.randomUUID(), "Finance", null));
		reader = role(app.id(), "Reader", "invoices:read");
		approver = role(app.id(), "Approver", "invoices:read", "invoices:approve");
	}

	private Role role(UUID application, String name, String... permissions) {
		var entitlements = java.util.Arrays.stream(permissions).map(Entitlement::new)
				.collect(java.util.stream.Collectors.toSet());
		return roles.save(new Role(UUID.randomUUID(), application, name, entitlements, null));
	}

	@Test
	void directAndGroupRolesOverlapWithoutDuplicatesAndRevocationsTakeEffect() {
		roles.assignToUser(reader.id(), user);
		roles.assignToUser(reader.id(), user);
		assertThat(resolver.resolve(user, app.id()).roles()).containsExactly("Reader");
		groups.addMember(finance.id(), user);
		groups.addMember(finance.id(), user);
		roles.assignToGroup(reader.id(), finance.id());
		roles.assignToGroup(approver.id(), finance.id());
		roles.assignToGroup(approver.id(), finance.id());
		var result = resolver.resolve(user, app.id());
		assertThat(result.roles()).containsExactlyInAnyOrder("Reader", "Approver");
		assertThat(result.entitlements()).containsExactlyInAnyOrder(new Entitlement("invoices:read"),
				new Entitlement("invoices:approve"));
		assertThat(result.groups()).containsExactly("Finance");
		roles.revokeFromUser(reader.id(), user);
		assertThat(resolver.resolve(user, app.id()).roles()).containsExactlyInAnyOrder("Reader", "Approver");
		roles.revokeFromGroup(reader.id(), finance.id());
		assertThat(resolver.resolve(user, app.id()).roles()).containsExactly("Approver");
		groups.removeMember(finance.id(), user);
		assertThat(resolver.resolve(user, app.id()).roles()).isEmpty();
		assertThat(resolver.resolve(user, app.id()).groups()).isEmpty();
	}

	@Test
	void noAccessDisabledUnknownAndCrossApplicationIsolation() {
		assertThat(resolver.resolve(user, app.id()).entitlements()).isEmpty();
		assertThat(resolver.resolve(user, app.id()).roles()).isEmpty();
		var other = applications.save(new Application(UUID.randomUUID(), "Other", "urn:other", null, null));
		var foreign = role(other.id(), "Reader", "other:admin");
		roles.assignToUser(foreign.id(), user);
		groups.addMember(finance.id(), user);
		roles.assignToGroup(foreign.id(), finance.id());
		assertThat(resolver.resolve(user, app.id()).roles()).isEmpty();
		assertThat(resolver.resolve(user, app.id()).entitlements()).isEmpty();
		assertThat(resolver.resolve(user, app.id()).groups()).containsExactly("Finance");
		assertThat(resolver.resolve(user, other.id()).entitlements()).containsExactly(new Entitlement("other:admin"));
		assertThat(resolver.resolve(user, UUID.randomUUID()).roles()).isEmpty();
		jdbc.update("UPDATE users SET enabled = false WHERE id = ?", user);
		assertThatThrownBy(() -> resolver.resolve(user, other.id())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> resolver.resolve(UUID.randomUUID(), app.id()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void threeQueriesRegardlessOfNumberOfRolesGroupsAndEntitlements() {
		assertQueryCount(0);
		roles.assignToUser(reader.id(), user);
		assertQueryCount(1);
		for (int i = 0; i < 30; i++) {
			var group = groups.save(new Group(UUID.randomUUID(), "Group" + i, null));
			groups.addMember(group.id(), user);
			var role = role(app.id(), "Role" + i, "read:" + i, "write:" + i);
			roles.assignToGroup(role.id(), group.id());
			roles.assignToUser(role.id(), user);
		}
		assertQueryCount(31);
		// A role with no entitlements must survive the left join.
		roles.assignToUser(role(app.id(), "Empty").id(), user);
		assertQueryCount(32);
	}

	private void assertQueryCount(int expectedRoles) {
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		assertThat(resolver.resolve(user, app.id()).roles()).hasSize(expectedRoles);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
	}

	@Test
	void aggregatesRoundTripAndRejectStaleVersionsIncludingEntitlementEdits() {
		assertThat(applications.findById(app.id())).contains(app);
		assertThat(groups.findById(finance.id())).contains(finance);
		assertThat(roles.findById(reader.id())).contains(reader);
		applications.save(new Application(app.id(), "Renamed", app.audience(), app.description(), app.version()));
		assertThatThrownBy(() -> applications.save(app)).isInstanceOf(OptimisticLockingFailureException.class);
		groups.save(new Group(finance.id(), "Renamed", finance.version()));
		assertThatThrownBy(() -> groups.save(finance)).isInstanceOf(OptimisticLockingFailureException.class);
		var updated = roles.save(new Role(reader.id(), app.id(), reader.name(),
				Set.of(new Entitlement("invoices:edit")), reader.version()));
		assertThat(updated.version()).isGreaterThan(reader.version());
		assertThat(roles.findById(reader.id())).contains(updated);
		assertThatThrownBy(() -> roles.save(reader)).isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test
	void databaseEnforcesReferencesApplicationRoleNamesAndEntitlementSyntax() {
		assertThatThrownBy(() -> role(app.id(), "Reader")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> roles.assignToUser(reader.id(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update("INSERT INTO role_entitlements VALUES (?, 'BAD PERMISSION')", reader.id()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void developmentSeedIsExplicitRepeatableAndResolvesExpectedAccess() throws Exception {
		// Avoid a name collision with the ordinary fixture.
		jdbc.update("DELETE FROM applications");
		jdbc.update("DELETE FROM groups");
		POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("../deploy/seed.sql").toAbsolutePath()),
				"/tmp/seed.sql");
		var missing = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(),
				"-f", "/tmp/seed.sql");
		assertThat(missing.getExitCode()).isNotZero();
		var unknown = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(),
				"-v", "seed_username=unknown", "-f", "/tmp/seed.sql");
		assertThat(unknown.getExitCode()).isNotZero();
		for (int i = 0; i < 2; i++) {
			var result = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(), "-d",
					POSTGRES.getDatabaseName(), "-v", "seed_username=access-admin", "-f", "/tmp/seed.sql");
			assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
		}
		var access = resolver.resolve(user, UUID.fromString("10000000-0000-0000-0000-000000000001"));
		assertThat(access.roles()).containsExactlyInAnyOrder("Reader", "Approver");
		assertThat(access.groups()).containsExactly("Finance");
		assertThat(access.entitlements()).containsExactlyInAnyOrder(new Entitlement("invoices:read"),
				new Entitlement("invoices:approve"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM applications", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM roles", Integer.class)).isEqualTo(2);
	}
}
