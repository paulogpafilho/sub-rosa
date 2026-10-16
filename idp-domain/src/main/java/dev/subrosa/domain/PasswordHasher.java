package dev.subrosa.domain;

public interface PasswordHasher {
	String hash(String password);

	boolean matches(String password, String hash);

	void dummyMatch(String password);
}
