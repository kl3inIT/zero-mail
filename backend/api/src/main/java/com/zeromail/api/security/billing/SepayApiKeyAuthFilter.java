package com.zeromail.api.security.billing;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import com.zeromail.core.billing.service.SepayApiKeyVerifier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;

public class SepayApiKeyAuthFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(SepayApiKeyAuthFilter.class);

  private final SepayApiKeyVerifier verifier;

  public SepayApiKeyAuthFilter(SepayApiKeyVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    return !request.getServletPath().startsWith("/api/billing/sepay/");
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws IOException, ServletException {
    String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (!verifier.verify(authorizationHeader)) {
      if (authorizationHeader == null) {
        log.warn("event=sepay_webhook_auth_missing");
      } else {
        log.warn("event=sepay_webhook_auth_invalid");
      }
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    chain.doFilter(request, response);
  }
}
