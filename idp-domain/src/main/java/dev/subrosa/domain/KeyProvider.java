package dev.subrosa.domain;

import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

public interface KeyProvider {
	SigningCredentials activeSigningKey();
	List<PublicKey> verificationKeys();

	record PublicKey(String kid, RSAPublicKey key, String certificatePem) {
	}

	record SigningCredentials(String kid, RSAPublicKey publicKey, PrivateKey privateKey) {
		@Override
		public String toString() {
			return "SigningCredentials[kid=" + kid + "]";
		}
	}
}
