package dev.subrosa.domain;

/** Hashes passwords and performs real or dummy password verification. */
public interface PasswordHasher {
  /** Hashes a password with a fresh salt after validating the password-length policy. */
  String hash(String password);

  /** Checks a supplied password against a stored password hash. */
  boolean matches(String password, String hash);

  /** Performs password-verification work when no account hash is available. */
  void dummyMatch(String password);
}
