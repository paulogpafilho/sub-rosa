package dev.subrosa.domain;

/** An atomic permission carried by a role. */
public record Entitlement(String value) {
	public Entitlement {
		if (value == null || value.length() > 200 || !value.matches("[a-z0-9_.:-]+")) {
			throw new IllegalArgumentException("Invalid entitlement");
		}
	}
}
