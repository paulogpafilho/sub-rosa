package dev.subrosa.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

final class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {
  static final String AUTHENTICATED_AT = "idp.authenticatedAt";
  private final Clock clock;
  private final Duration timeout;

  AbsoluteSessionTimeoutFilter(Clock clock, Duration timeout) {
    this.clock = clock;
    this.timeout = timeout;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var session = request.getSession(false);
    if (session != null
        && session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
            != null) {
      var authenticatedAt = session.getAttribute(AUTHENTICATED_AT);
      if (!(authenticatedAt instanceof Long timestamp)
          || !clock.instant().isBefore(java.time.Instant.ofEpochMilli(timestamp).plus(timeout))) {
        session.invalidate();
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
