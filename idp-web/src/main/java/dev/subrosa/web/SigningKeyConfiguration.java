package dev.subrosa.web;

import java.time.Clock;
import dev.subrosa.domain.SigningKeyRepository;
import dev.subrosa.oidc.JwtSignatures;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SigningKeyConfiguration {
	@Bean
	StoredKeyProvider keyProvider(SigningKeyRepository repository, Clock clock,
			@Value("${IDP_KEY_ENCRYPTION_SECRET:}") String secret) {
		var provider = new StoredKeyProvider(repository, new SigningKeyCodec(new PrivateKeyEncryption(secret)), clock);
		provider.initialize();
		return provider;
	}

	@Bean
	JwtSignatures jwtSignatures(StoredKeyProvider keys) {
		return new JwtSignatures(keys);
	}
}
