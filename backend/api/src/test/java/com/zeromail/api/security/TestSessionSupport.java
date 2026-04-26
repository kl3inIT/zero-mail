package com.zeromail.api.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Test-only auth shim. Replaces Spring Session cookie resolution with a header-based
 * filter that reads {@code X-Test-Subject} + {@code X-Test-Email} and sets both
 * {@code SecurityContextHolder} (so Spring Security's authorization rules pass) and
 * {@code TenantContext.TENANT} ScopedValue (so controllers see the right tenant
 * without round-tripping through {@link TenantBindingFilter}'s session-backed lookup).
 *
 * Contributes a {@link SecurityFilterChain} ordered above the main one in
 * {@link SecurityConfig}, so test traffic uses this chain. The chain disables CSRF
 * and is stateless (no session creation).
 */
@TestConfiguration
public class TestSessionSupport {

    public static final String HEADER_SUBJECT = "X-Test-Subject";
    public static final String HEADER_EMAIL = "X-Test-Email";

    public interface TestSessionMinter {
        /** Convenience no-op: kept so existing test call sites that pass a "cookie" still compile. */
        String mint(String googleSubject, String email);
    }

    @Bean
    TestSessionMinter testSessionMinter() {
        return (googleSubject, email) -> "X-Test-Subject=" + googleSubject;
    }

    @Bean
    OncePerRequestFilter testAuthFilter(UserRepository users) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String subject = req.getHeader(HEADER_SUBJECT);
                String email = req.getHeader(HEADER_EMAIL);
                if (subject == null || email == null) {
                    chain.doFilter(req, res);
                    return;
                }
                var claims = Map.<String, Object>of("sub", subject, "email", email);
                var idToken = new OidcIdToken(
                        "test-id-token-" + subject,
                        java.time.Instant.now(),
                        java.time.Instant.now().plusSeconds(3600),
                        claims);
                var principal = new DefaultOidcUser(
                        List.of(new SimpleGrantedAuthority("OIDC_USER")),
                        idToken);
                var authToken = new OAuth2AuthenticationToken(
                        principal,
                        principal.getAuthorities(),
                        "google");
                SecurityContextHolder.getContext().setAuthentication(authToken);

                Optional<UserEntity> userOpt = users.findByGoogleSubject(subject);
                try {
                    if (userOpt.isPresent()) {
                        UUID tenantId = userOpt.get().getTenantId();
                        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
                            try {
                                chain.doFilter(req, res);
                            } catch (IOException | ServletException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } else {
                        chain.doFilter(req, res);
                    }
                } catch (RuntimeException re) {
                    if (re.getCause() instanceof IOException io) throw io;
                    if (re.getCause() instanceof ServletException se) throw se;
                    throw re;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
        };
    }

    /**
     * Highest-priority SecurityFilterChain. Matches everything; permits everything;
     * inserts the test auth filter so SecurityContext + ScopedValue are populated
     * before any controller runs. Order 0 wins against SecurityConfig's default-100 chain.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain testSecurityChain(HttpSecurity http, OncePerRequestFilter testAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(testAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
