package dev.subrosa.web;

import java.net.URI;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("idp")
public record IdpProperties(@NotNull URI issuer) {

	@AssertTrue(message = "issuer must be an absolute HTTP(S) URL without credentials, query or fragment")
	public boolean isIssuerValid() {
		return issuer != null && issuer.getHost() != null
				&& ("https".equals(issuer.getScheme()) || "http".equals(issuer.getScheme()))
				&& issuer.getUserInfo() == null && issuer.getQuery() == null && issuer.getFragment() == null;
	}
}
