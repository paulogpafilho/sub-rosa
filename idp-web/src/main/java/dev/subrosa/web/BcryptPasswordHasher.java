package dev.subrosa.web;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import dev.subrosa.domain.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

final class BcryptPasswordHasher implements PasswordHasher {
	private final BCryptPasswordEncoder encoder;
	private final int maximumBytes;
	private final String dummyHash;

	BcryptPasswordHasher(int cost, int maximumBytes) {
		this.encoder = new BCryptPasswordEncoder(cost);
		this.maximumBytes = maximumBytes;
		this.dummyHash = encoder.encode(UUID.randomUUID().toString());
	}

	private boolean valid(String password) {
		return password != null && !password.isEmpty() && password.length() <= maximumBytes
				&& password.getBytes(StandardCharsets.UTF_8).length <= maximumBytes;
	}

	@Override
	public String hash(String password) {
		if (!valid(password)) {
			throw new IllegalArgumentException("Password must be nonempty and within the configured UTF-8 byte limit");
		}
		return encoder.encode(password);
	}

	@Override
	public boolean matches(String password, String hash) {
		if (!valid(password)) {
			dummyMatch(null);
			return false;
		}
		return encoder.matches(password, hash);
	}

	@Override
	public void dummyMatch(String password) {
		encoder.matches(valid(password) ? password : "invalid", dummyHash);
	}
}
