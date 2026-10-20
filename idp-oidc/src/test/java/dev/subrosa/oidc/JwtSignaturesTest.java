package dev.subrosa.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.util.Base64URL;
import dev.subrosa.domain.KeyProvider;
import org.junit.jupiter.api.Test;

class JwtSignaturesTest {
	@Test
	void signsWithKidAndVerifiesUsingPublishedPublicJwk() throws Exception {
		var pair = pair();
		var signatures = new JwtSignatures(provider(pair));
		String token = signatures.sign(claims(), JOSEObjectType.JWT);
		var jwt = SignedJWT.parse(token);
		assertEquals("test-key", jwt.getHeader().getKeyID());
		assertEquals(JWSAlgorithm.RS256, jwt.getHeader().getAlgorithm());
		assertEquals("user-1", signatures.verifySignature(token).getSubject());
		var publicSet = JWKSet.parse(signatures.publicJwkSet());
		assertFalse(publicSet.getKeys().getFirst().isPrivate());
		assertEquals(pair.getPublic(), publicSet.getKeys().getFirst().toRSAKey().toRSAPublicKey());
		assertEquals(true,
				jwt.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(publicSet.getKeys().getFirst().toRSAKey())));
	}

	@Test
	void rejectsTamperingNoneWrongAlgorithmsKeysAndUntrustedHeaders() throws Exception {
		var pair = pair();
		var signatures = new JwtSignatures(provider(pair));
		var good = SignedJWT.parse(signatures.sign(claims(), JOSEObjectType.JWT));
		reject(signatures, good.getHeader().toBase64URL() + "." + Base64URL.encode("{\"sub\":\"attacker\"}") + "."
				+ good.getSignature());
		reject(signatures, new PlainJWT(claims()).serialize());
		reject(signatures, "malformed");
		reject(signatures, null);
		reject(signatures, "x".repeat(65537));
		for (JWSHeader header : List.of(new JWSHeader.Builder(JWSAlgorithm.RS384).keyID("test-key").build(),
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("unknown").build(),
				new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").criticalParams(java.util.Set.of("custom"))
						.customParam("custom", true).build())) {
			var jwt = new SignedJWT(header, claims());
			jwt.sign(new RSASSASigner(pair.getPrivate()));
			reject(signatures, jwt.serialize());
		}
		var wrongKey = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims());
		wrongKey.sign(new RSASSASigner(pair().getPrivate()));
		reject(signatures, wrongKey.serialize());
		var hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("test-key").build(), claims());
		hmac.sign(new MACSigner(pair.getPublic().getEncoded()));
		reject(signatures, hmac.serialize());
	}

	private void reject(JwtSignatures signatures, String token) {
		assertThrows(IllegalArgumentException.class, () -> signatures.verifySignature(token));
	}

	private JWTClaimsSet claims() {
		return new JWTClaimsSet.Builder().subject("user-1").build();
	}

	private KeyPair pair() throws Exception {
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	private KeyProvider provider(KeyPair pair) {
		return new KeyProvider() {
			public SigningCredentials activeSigningKey() {
				return new SigningCredentials("test-key", (RSAPublicKey) pair.getPublic(), pair.getPrivate());
			}
			public List<PublicKey> verificationKeys() {
				return List.of(new PublicKey("test-key", (RSAPublicKey) pair.getPublic(), "certificate"));
			}
		};
	}
}
