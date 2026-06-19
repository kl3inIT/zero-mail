package com.zeromail.api.security;

import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.billing.config.BillingProperties;
import com.zeromail.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Test-only auth shim. Replaces Spring Session cookie resolution with a header-based filter that
 * reads {@code X-Test-Subject} + {@code X-Test-Email} and sets both {@code SecurityContextHolder}
 * (so Spring Security's authorization rules pass) and {@code TenantContext.TENANT} ScopedValue (so
 * controllers see the right tenant without round-tripping through {@link TenantBindingFilter}'s
 * session-backed lookup).
 *
 * <p>Contributes a {@link SecurityFilterChain} after the Pub/Sub OIDC chain, so test traffic for
 * user-session endpoints uses this chain while machine-authenticated {@code /internal/pubsub/**}
 * requests remain owned by {@link PubSubSecurityConfig}.
 */
@TestConfiguration
public class TestSessionSupport {

    public static final String HEADER_SUBJECT = "X-Test-Subject";
    public static final String HEADER_EMAIL = "X-Test-Email";
    public static final String HEADER_NAME = "X-Test-Name";
    public static final String HEADER_PICTURE = "X-Test-Picture";

    public interface TestSessionMinter {
        /**
         * Convenience no-op: kept so existing test call sites that pass a "cookie" still compile.
         */
        void mint(String googleSubject, String email);
    }

    @Bean
    TestSessionMinter testSessionMinter() {
        return (googleSubject, email) -> {
            Objects.requireNonNull(googleSubject, "googleSubject");
            Objects.requireNonNull(email, "email");
        };
    }

    @Bean
    OncePerRequestFilter testAuthFilter(UserRepository users) {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilterAsyncDispatch() {
                return false;
            }

            @Override
            protected boolean shouldNotFilterErrorDispatch() {
                return false;
            }

            @Override
            protected void doFilterInternal(
                    @NonNull HttpServletRequest req,
                    @NonNull HttpServletResponse res,
                    @NonNull FilterChain chain)
                    throws ServletException, IOException {
                String subject = req.getHeader(HEADER_SUBJECT);
                String email = req.getHeader(HEADER_EMAIL);
                if (subject == null || email == null) {
                    chain.doFilter(req, res);
                    return;
                }
                var claims = new HashMap<String, Object>();
                claims.put("sub", subject);
                claims.put("email", email);
                String name = req.getHeader(HEADER_NAME);
                if (name != null) {
                    claims.put("name", name);
                }
                String picture = req.getHeader(HEADER_PICTURE);
                if (picture != null) {
                    claims.put("picture", picture);
                }
                var idToken =
                        new OidcIdToken(
                                "test-id-token-" + subject,
                                java.time.Instant.now(),
                                java.time.Instant.now().plusSeconds(3600),
                                claims);
                var principal =
                        new DefaultOidcUser(
                                List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
                var authToken =
                        new OAuth2AuthenticationToken(
                                principal, principal.getAuthorities(), "google");
                SecurityContextHolder.getContext().setAuthentication(authToken);

                Optional<UserEntity> userOpt = users.findByGoogleSubject(subject);
                if (userOpt.isEmpty()) {
                    SecurityContextHolder.clearContext();
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                try {
                    UUID tenantId = userOpt.get().getTenantId();
                    ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                            .run(
                                    () -> {
                                        try {
                                            chain.doFilter(req, res);
                                        } catch (IOException | ServletException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
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
     * Test user-session SecurityFilterChain. It explicitly excludes Pub/Sub endpoints, requires
     * authentication for everything else, and inserts the test auth filter so SecurityContext +
     * ScopedValue are populated before any controller runs.
     */
    @Bean
    @Order(2)
    SecurityFilterChain testLemonSqueezyWebhookChain(
            HttpSecurity http, BillingProperties billingProperties) throws Exception {
        return http.securityMatcher("/api/plan-upgrades/webhooks/lemon-squeezy")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        authorizationRequests ->
                                authorizationRequests
                                        .requestMatchers(
                                                "/api/plan-upgrades/webhooks/lemon-squeezy")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new LemonSqueezyWebhookSignatureFilter(billingProperties),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain testSepayWebhookChain(
            HttpSecurity http, BillingProperties billingProperties) throws Exception {
        return http.securityMatcher("/api/plan-upgrades/webhooks/sepay")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        authorizationRequests ->
                                authorizationRequests
                                        .requestMatchers("/api/plan-upgrades/webhooks/sepay")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new SepayWebhookApiKeyFilter(billingProperties),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain testSecurityChain(HttpSecurity http, OncePerRequestFilter testAuthFilter) {
        // Must not overlap with PubSubSecurityConfig @Order(1) (/internal/pubsub/**) — Spring
        // Security 7's WebSecurityFilterChainValidator throws UnreachableFilterChainException when
        // an earlier chain's matcher is fully shadowed by a later one with the same order.
        RequestMatcher userChainMatcher =
                request -> {
                    String path = request.getServletPath();
                    return !path.startsWith("/internal/pubsub/");
                };
        return http.securityMatcher(userChainMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers("/v3/api-docs/**", "/test/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                        (_, res, _) ->
                                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(testAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
