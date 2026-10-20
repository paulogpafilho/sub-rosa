package dev.subrosa.web;

import java.util.Map;
import dev.subrosa.oidc.JwtSignatures;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class JwksController {
	private final JwtSignatures signatures;

	JwksController(JwtSignatures signatures) {
		this.signatures = signatures;
	}

	@GetMapping("/oauth2/jwks")
	Map<String, Object> jwks() {
		return signatures.publicJwkSet();
	}
}
