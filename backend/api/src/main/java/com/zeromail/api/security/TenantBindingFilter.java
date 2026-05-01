package com.zeromail.api.security;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;

@Component
public class TenantBindingFilter extends OncePerRequestFilter {

  private final UserRepository userRepository;

  public TenantBindingFilter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      chain.doFilter(request, response);
      return;
    }
    var user = userRepository.findByGoogleSubject(oidcUser.getSubject()).orElse(null);
    if (user == null) {
      chain.doFilter(request, response);
      return;
    }
    final String tenantId = user.getTenantId().toString();
    try {
      ScopedValue.where(TenantContext.TENANT, tenantId)
          .run(
              () -> {
                try {
                  chain.doFilter(request, response);
                } catch (IOException | ServletException filterException) {
                  throw new RuntimeException(filterException);
                }
              });
    } catch (RuntimeException runtimeException) {
      if (runtimeException.getCause() instanceof IOException ioException) throw ioException;
      if (runtimeException.getCause() instanceof ServletException servletException) throw servletException;
      throw runtimeException;
    }
  }
}
