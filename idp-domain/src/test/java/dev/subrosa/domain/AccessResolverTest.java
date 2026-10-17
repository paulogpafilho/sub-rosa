package dev.subrosa.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessResolverTest {
	private final UUID user = UUID.randomUUID();
	private final UUID app = UUID.randomUUID();
	private final Entitlement read = new Entitlement("invoices:read");

	@Test
	void unionsRolesAndEntitlementsAndFiltersOtherApplications() {
		var reader = new Role(UUID.randomUUID(), app, "Reader", Set.of(read), 0L);
		var approver = new Role(UUID.randomUUID(), app, "Approver", Set.of(read, new Entitlement("invoices:approve")),
				0L);
		var foreign = new Role(UUID.randomUUID(), UUID.randomUUID(), "Other", Set.of(new Entitlement("other:admin")),
				0L);
		var group = new Group(UUID.randomUUID(), "Finance", 0L);
		var resolver = new AccessResolver((u, a) -> {
			assertEquals(user, u);
			assertEquals(app, a);
			return Optional.of(new AccessRepository.Snapshot(true, Set.of(group), Set.of(reader, approver, foreign)));
		});
		assertEquals(new EffectiveAccess(Set.of("Reader", "Approver"),
				Set.of(read, new Entitlement("invoices:approve")), Set.of("Finance")), resolver.resolve(user, app));
	}

	@Test
	void noRolesStillPreservesGlobalMembership() {
		var resolver = new AccessResolver((u, a) -> Optional.of(
				new AccessRepository.Snapshot(true, Set.of(new Group(UUID.randomUUID(), "Finance", 0L)), Set.of())));
		assertEquals(new EffectiveAccess(Set.of(), Set.of(), Set.of("Finance")), resolver.resolve(user, app));
	}

	@Test
	void disabledAndUnknownUsersAreRejected() {
		var disabled = new AccessResolver(
				(u, a) -> Optional.of(new AccessRepository.Snapshot(false, Set.of(), Set.of())));
		assertThrows(IllegalArgumentException.class, () -> disabled.resolve(user, app));
		assertThrows(IllegalArgumentException.class,
				() -> new AccessResolver((u, a) -> Optional.empty()).resolve(user, app));
	}

	@Test
	void entitlementsAreValidatedAndCollectionsAreImmutable() {
		for (String value : new String[]{"", "Admin", "has space", "invoices:*", "x".repeat(201)}) {
			assertThrows(IllegalArgumentException.class, () -> new Entitlement(value));
		}
		assertThrows(IllegalArgumentException.class, () -> new Entitlement(null));
		var result = new EffectiveAccess(Set.of("Reader"), Set.of(read), Set.of());
		assertThrows(UnsupportedOperationException.class, () -> result.roles().clear());
	}
}
