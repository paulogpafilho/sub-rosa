package dev.subrosa.web;

import dev.subrosa.domain.AccessRepository;
import dev.subrosa.domain.AccessResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AccessConfiguration {
	@Bean
	AccessResolver accessResolver(AccessRepository access) {
		return new AccessResolver(access);
	}
}
