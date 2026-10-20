package dev.subrosa.persistence;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import dev.subrosa.domain.SigningKey;

@Entity
@Table(name = "signing_keys")
class SigningKeyEntity {
	protected SigningKeyEntity() {
	}
	@Id
	@Column(length = 100)
	String kid;
	@Column(name = "private_key_enc", nullable = false, columnDefinition = "text")
	String encrypted;
	@Column(name = "public_pem", nullable = false, columnDefinition = "text")
	String publicPem;
	@Column(name = "cert_pem", nullable = false, columnDefinition = "text")
	String certificatePem;
	@Column(nullable = false, length = 10)
	String status;
	@Column(name = "created_at", nullable = false)
	Instant createdAt;
	@Column(name = "retire_at")
	Instant retireAt;

	static SigningKeyEntity from(SigningKey key) {
		var entity = new SigningKeyEntity();
		entity.kid = key.kid();
		entity.encrypted = key.privateKeyEncrypted();
		entity.publicPem = key.publicPem();
		entity.certificatePem = key.certificatePem();
		entity.status = key.status().name();
		entity.createdAt = key.createdAt();
		entity.retireAt = key.retireAt();
		return entity;
	}

	SigningKey snapshot() {
		return new SigningKey(kid, encrypted, publicPem, certificatePem, SigningKey.Status.valueOf(status), createdAt,
				retireAt);
	}
}
