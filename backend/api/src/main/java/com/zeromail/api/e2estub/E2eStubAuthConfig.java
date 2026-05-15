package com.zeromail.api.e2estub;

import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@Profile("e2e-stub")
@ConditionalOnProperty(name = "zeromail.e2e-stub.enabled", havingValue = "true")
public class E2eStubAuthConfig {

    public static final String HEADER_SUBJECT = "X-Test-Subject";
    public static final String HEADER_EMAIL = "X-Test-Email";

    @Bean
    public OncePerRequestFilter e2eStubAuthFilter(UserRepository userRepository) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    @NonNull HttpServletRequest request,
                    @NonNull HttpServletResponse response,
                    @NonNull FilterChain filterChain)
                    throws ServletException, IOException {
                String googleSubject = request.getHeader(HEADER_SUBJECT);
                String email = request.getHeader(HEADER_EMAIL);
                if (googleSubject == null || email == null) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Optional<UserEntity> optionalUser =
                        userRepository.findByGoogleSubject(googleSubject);
                if (optionalUser.isEmpty()) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                SecurityContextHolder.getContext()
                        .setAuthentication(authenticationToken(googleSubject, email));
                try {
                    UUID tenantId = optionalUser.orElseThrow().getTenantId();
                    ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                            .run(() -> continueFilterChain(filterChain, request, response));
                } catch (FilterChainRuntimeException filterChainRuntimeException) {
                    Throwable cause = filterChainRuntimeException.getCause();
                    if (cause instanceof IOException ioException) {
                        throw ioException;
                    }
                    if (cause instanceof ServletException servletException) {
                        throw servletException;
                    }
                    throw filterChainRuntimeException;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
        };
    }

    @Bean
    @Order(2)
    public SecurityFilterChain e2eStubSecurityChain(
            HttpSecurity http, OncePerRequestFilter e2eStubAuthFilter) throws Exception {
        RequestMatcher nonPubSubRequestMatcher =
                request -> !request.getServletPath().startsWith("/internal/pubsub/");
        return http.securityMatcher(nonPubSubRequestMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorization ->
                                authorization
                                        .requestMatchers(
                                                "/actuator/health/**",
                                                "/v3/api-docs/**",
                                                "/api/test/e2e-stub/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        exceptionHandling ->
                                exceptionHandling.authenticationEntryPoint(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(e2eStubAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static OAuth2AuthenticationToken authenticationToken(
            String googleSubject, String email) {
        Map<String, Object> claims = Map.of("sub", googleSubject, "email", email);
        OidcIdToken idToken =
                new OidcIdToken(
                        "e2e-stub-id-token-" + googleSubject,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        claims);
        DefaultOidcUser principal =
                new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    private static void continueFilterChain(
            FilterChain filterChain, HttpServletRequest request, HttpServletResponse response) {
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException filterChainException) {
            throw new FilterChainRuntimeException(filterChainException);
        }
    }

    private static final class FilterChainRuntimeException extends RuntimeException {

        private FilterChainRuntimeException(Throwable cause) {
            super(cause);
        }
    }
}
