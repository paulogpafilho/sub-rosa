package dev.subrosa.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment, IdpProperties properties)
			throws Exception {
		if (!environment.acceptsProfiles(Profiles.of("dev"))) {
			if (!"https".equals(properties.issuer().getScheme())) {
				throw new IllegalArgumentException("An HTTPS issuer is required outside the dev profile");
			}
			http.redirectToHttps(Customizer.withDefaults());
		}
		http.authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
				.anyRequest().denyAll());
		http.exceptionHandling(
				exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)));
		http.requestCache(cache -> cache.disable());
		return http.build();
	}
}
