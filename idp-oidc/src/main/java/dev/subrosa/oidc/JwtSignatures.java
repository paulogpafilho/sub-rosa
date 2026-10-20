package dev.subrosa.oidc;

import java.text.ParseException;
import java.util.Map;
import dev.subrosa.domain.KeyProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Signature primitives only. Callers must separately validate claims and token
 * type.
 */
public final class JwtSignatures {
	private final KeyProvider keys;

	public JwtSignatures(KeyProvider keys) {
		this.keys = keys;
	}

	public String sign(JWTClaimsSet claims, JOSEObjectType type) {
		var key = keys.activeSigningKey();
		var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.kid()).type(type).build(), claims);
		try {
			jwt.sign(new RSASSASigner(key.privateKey()));
			return jwt.serialize();
		} catch (JOSEException exception) {
			throw new IllegalStateException("Unable to sign JWT");
		}
	}

	public JWTClaimsSet verifySignature(String token) {
		try {
			if (token == null || token.length() > 65536) {
				throw new IllegalArgumentException();
			}
			var jwt = SignedJWT.parse(token);
			var header = jwt.getHeader();
			if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || header.getKeyID() == null
					|| header.getKeyID().isBlank() || header.getCriticalParams() != null
					|| !header.isBase64URLEncodePayload()) {
				throw new IllegalArgumentException();
			}
			var key = keys.verificationKeys().stream().filter(candidate -> candidate.kid().equals(header.getKeyID()))
					.findFirst().orElseThrow(IllegalArgumentException::new);
			if (!jwt.verify(new RSASSAVerifier(key.key()))) {
				throw new IllegalArgumentException();
			}
			return jwt.getJWTClaimsSet();
		} catch (ParseException | JOSEException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid JWT signature");
		}
	}

	public Map<String, Object> publicJwkSet() {
		var publicKeys = keys.verificationKeys()
				.stream().<com.nimbusds.jose.jwk.JWK>map(key -> new RSAKey.Builder(key.key()).keyID(key.kid())
						.keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build())
				.toList();
		return new JWKSet(publicKeys).toJSONObject();
	}
}
