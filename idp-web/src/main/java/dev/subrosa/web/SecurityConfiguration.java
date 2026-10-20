package dev.subrosa.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import java.time.Clock;
import dev.subrosa.domain.AuthenticationService;
import jakarta.servlet.DispatcherType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment, IdpProperties properties,
			AuthenticationService authentication, IpLoginThrottle throttle, Clock clock, IdentityProperties identity)
			throws Exception {
		if (!environment.acceptsProfiles(Profiles.of("dev"))) {
			if (!"https".equals(properties.issuer().getScheme())) {
				throw new IllegalArgumentException("An HTTPS issuer is required outside the dev profile");
			}
			http.redirectToHttps(Customizer.withDefaults());
		}
		http.authenticationProvider(new IdentityAuthenticationProvider(authentication, throttle));
		http.authorizeHttpRequests(requests -> requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/login", "/oauth2/jwks").permitAll()
				.requestMatchers(HttpMethod.POST, "/login", "/logout").permitAll().requestMatchers(HttpMethod.GET, "/")
				.authenticated().anyRequest().denyAll());
		http.formLogin(form -> form.loginPage("/login").failureUrl("/login?error")
				.successHandler((request, response, result) -> {
					request.getSession().setAttribute(AbsoluteSessionTimeoutFilter.AUTHENTICATED_AT, clock.millis());
					response.sendRedirect(request.getContextPath() + "/");
				}));
		http.logout(logout -> logout.logoutSuccessUrl("/login?logout").invalidateHttpSession(true)
				.clearAuthentication(true).deleteCookies("SESSION"));
		http.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
		http.addFilterBefore(new AbsoluteSessionTimeoutFilter(clock, identity.sessionAbsoluteTimeout()),
				UsernamePasswordAuthenticationFilter.class);
		http.requestCache(cache -> cache.disable());
		return http.build();
	}
}
