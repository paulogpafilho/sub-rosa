package dev.subrosa.web;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.security.MessageDigest;
import dev.subrosa.domain.KeyProvider;
import dev.subrosa.domain.SigningKey;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** JCA and Bouncy Castle adapter. No library objects escape into the domain. */
final class SigningKeyCodec {
	private final PrivateKeyEncryption encryption;

	SigningKeyCodec(PrivateKeyEncryption encryption) {
		this.encryption = encryption;
	}

	SigningKey generate(Instant now) {
		byte[] privateBytes = null;
		try {
			var generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			var pair = generator.generateKeyPair();
			String kid = HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(pair.getPublic().getEncoded()));
			var name = new X500Name("CN=Sub-Rosa Signing");
			var certificate = new JcaX509v3CertificateBuilder(name,
					new BigInteger(159, new SecureRandom()).add(BigInteger.ONE), Date.from(now.minusSeconds(60)),
					Date.from(now.plus(Duration.ofDays(3650))), name, pair.getPublic());
			certificate.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
			certificate.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
			var signed = certificate.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate()));
			var x509 = new JcaX509CertificateConverter().getCertificate(signed);
			privateBytes = pair.getPrivate().getEncoded();
			return new SigningKey(kid, encryption.encrypt(kid, privateBytes), pem(pair.getPublic()), pem(x509),
					SigningKey.Status.ACTIVE, now, null);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to generate signing key");
		} finally {
			if (privateBytes != null) {
				Arrays.fill(privateBytes, (byte) 0);
			}
		}
	}

	KeyProvider.SigningCredentials credentials(SigningKey key) {
		byte[] bytes = encryption.decrypt(key.kid(), key.privateKeyEncrypted());
		try {
			var privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
			var publicKey = publicKey(key).key();
			// Detect mismatched private/public material before serving requests.
			var signature = java.security.Signature.getInstance("SHA256withRSA");
			signature.initSign(privateKey);
			signature.update(key.kid().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			byte[] proof = signature.sign();
			signature.initVerify(publicKey);
			signature.update(key.kid().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			if (!signature.verify(proof)) {
				throw new IllegalStateException();
			}
			return new KeyProvider.SigningCredentials(key.kid(), publicKey, privateKey);
		} catch (Exception exception) {
			throw new IllegalStateException("Invalid signing key material");
		} finally {
			Arrays.fill(bytes, (byte) 0);
		}
	}

	KeyProvider.PublicKey publicKey(SigningKey key) {
		try {
			String encoded = key.publicPem().replace("-----BEGIN PUBLIC KEY-----", "")
					.replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
			var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
					.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
			if (publicKey.getModulus().bitLength() != 2048) {
				throw new IllegalArgumentException();
			}
			return new KeyProvider.PublicKey(key.kid(), publicKey, key.certificatePem());
		} catch (Exception exception) {
			throw new IllegalStateException("Invalid public signing key");
		}
	}

	private String pem(Object object) throws java.io.IOException {
		var output = new StringWriter();
		try (var writer = new JcaPEMWriter(output)) {
			writer.writeObject(object);
		}
		return output.toString();
	}
}
