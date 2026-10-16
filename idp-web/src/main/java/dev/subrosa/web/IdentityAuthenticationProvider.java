package dev.subrosa.web;

import java.util.List;
import dev.subrosa.domain.AuthenticationService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

final class IdentityAuthenticationProvider implements AuthenticationProvider {
	private final AuthenticationService authentication;
	private final IpLoginThrottle throttle;

	IdentityAuthenticationProvider(AuthenticationService authentication, IpLoginThrottle throttle) {
		this.authentication = authentication;
		this.throttle = throttle;
	}

	@Override
	public Authentication authenticate(Authentication request) {
		if (!(request.getDetails() instanceof WebAuthenticationDetails details)
				|| !throttle.allow(details.getRemoteAddress())) {
			throw new BadCredentialsException("Invalid credentials");
		}
		var user = authentication.authenticate(request.getName(), (String) request.getCredentials())
				.orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
		// Store only an immutable subject identifier in the distributed session, never
		// a hash/entity.
		return UsernamePasswordAuthenticationToken.authenticated(user.id().toString(), null, List.of());
	}

	@Override
	public boolean supports(Class<?> type) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
	}
}
