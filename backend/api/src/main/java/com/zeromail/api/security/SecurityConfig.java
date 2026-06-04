package com.zeromail.api.security;

import com.zeromail.core.admin.auth.usecases.AdminUserDetailsService;
import com.zeromail.core.billing.config.BillingProperties;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@Profile("!test & !e2e-stub")
public class SecurityConfig {

    private static final PathPatternRequestMatcher API_REQUEST_MATCHER =
            PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    private static final PathPatternRequestMatcher ADMIN_REQUEST_MATCHER =
            PathPatternRequestMatcher.withDefaults().matcher("/api/admin/**");

    // Production defaults; override via env for local dev (rpId=localhost,
    // allowedOrigins=http://localhost:5174). Browser-side WebAuthn rejects any
    // ceremony where the rpId is not a registrable suffix of the current origin.
    @Value("${zero-mail.admin.webauthn.rp-id:admin.zeromail.vn}")
    private String adminWebAuthnRpId;

    @Value("${zero-mail.admin.webauthn.rp-name:Zero Mail Admin}")
    private String adminWebAuthnRpName;

    @Value("${zero-mail.admin.webauthn.allowed-origins:https://admin.zeromail.vn}")
    private Set<String> adminWebAuthnAllowedOrigins;

    @Bean
    @Order(1)
    SecurityFilterChain adminChain(
            HttpSecurity http,
            AdminBindingFilter adminBindingFilter,
            AdminResponseBodyBanFilter adminResponseBodyBanFilter,
            AdminUserDetailsService adminUserDetailsService,
            EnrollmentSessionAuthFilter enrollmentSessionAuthFilter)
            throws Exception {
        // see docs/ops/admin-interface-freeze.md §Spring Security WebAuthn Endpoints
        http.securityMatcher("/api/admin/**", "/webauthn/**", "/login/webauthn/**")
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(
                        authorizationRequests ->
                                authorizationRequests
                                        .requestMatchers("/webauthn/**", "/login/webauthn/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/admin/enrollment/session")
                                        .permitAll()
                                        .anyRequest()
                                        .hasRole("ADMIN"))
                .webAuthn(
                        webAuthn ->
                                webAuthn.rpName(adminWebAuthnRpName)
                                        .rpId(adminWebAuthnRpId)
                                        .allowedOrigins(
                                                adminWebAuthnAllowedOrigins.toArray(new String[0]))
                                        .disableDefaultRegistrationPage(true))
                .userDetailsService(adminUserDetailsService)
                .csrf(
                        csrf ->
                                csrf.spa()
                                        .ignoringRequestMatchers(
                                                "/api/admin/enrollment/session",
                                                "/webauthn/**",
                                                "/login/webauthn/**"))
                .exceptionHandling(
                        exceptionHandling ->
                                exceptionHandling.defaultAuthenticationEntryPointFor(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                        ADMIN_REQUEST_MATCHER))
                .logout(
                        logout ->
                                logout.logoutRequestMatcher(
                                                PathPatternRequestMatcher.withDefaults()
                                                        .matcher(
                                                                HttpMethod.POST,
                                                                "/api/admin/logout"))
                                        .logoutSuccessHandler(
                                                new HttpStatusReturningLogoutSuccessHandler())
                                        .invalidateHttpSession(true)
                                        .deleteCookies("JSESSIONID"))
                .sessionManagement(Customizer.withDefaults())
                .addFilterAfter(enrollmentSessionAuthFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(adminResponseBodyBanFilter, AuthorizationFilter.class)
                .addFilterAfter(adminBindingFilter, AdminResponseBodyBanFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain lemonSqueezyWebhookChain(
            HttpSecurity http, BillingProperties billingProperties) throws Exception {
        http.securityMatcher("/api/plan-upgrades/webhooks/lemon-squeezy")
                .csrf(
                        csrf ->
                                csrf.spa()
                                        .ignoringRequestMatchers(
                                                "/api/plan-upgrades/webhooks/lemon-squeezy"))
                .authorizeHttpRequests(
                        authorizationRequests ->
                                authorizationRequests
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/plan-upgrades/webhooks/lemon-squeezy")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new LemonSqueezyWebhookSignatureFilter(billingProperties),
                        AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain sepayWebhookChain(HttpSecurity http, BillingProperties billingProperties)
            throws Exception {
        http.securityMatcher("/api/plan-upgrades/webhooks/sepay")
                .csrf(
                        csrf ->
                                csrf.spa()
                                        .ignoringRequestMatchers(
                                                "/api/plan-upgrades/webhooks/sepay"))
                .authorizeHttpRequests(
                        authorizationRequests ->
                                authorizationRequests
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/plan-upgrades/webhooks/sepay")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new SepayWebhookApiKeyFilter(billingProperties), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain chain(
            HttpSecurity http,
            TenantBindingFilter tenantFilter,
            GoogleOAuthSuccessHandler successHandler,
            LoginRedirectAuthenticationFailureHandler failureHandler,
            GoogleAuthorizationRequestResolver authRequestResolver) {
        // Default catch-all for user-session traffic. Explicit securityMatcher excluding
        // chains owned by earlier @Order beans (PubSub @Order(1), AdminChain @Order(1),
        // plan-upgrade webhook @Order(2/3)) so Spring Security 7's WebSecurityFilterChainValidator
        // does not flag this chain as shadowing a more-specific matcher.
        RequestMatcher userChainMatcher =
                request -> {
                    String path = request.getServletPath();
                    return !path.startsWith("/internal/pubsub/")
                            && !path.startsWith("/api/admin/")
                            && !path.startsWith("/webauthn/")
                            && !path.startsWith("/login/webauthn/");
                };
        http.securityMatcher(userChainMatcher)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(
                        authorizationRequests ->
                                authorizationRequests
                                        .requestMatchers(
                                                "/login",
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                // Prometheus scrape target. Exposes operational
                                                // metrics only (JVM/GC/HTTP counters) — never
                                                // email,
                                                // tokens, prompts, or PII (privacy invariant). The
                                                // reverse proxy must NOT route /actuator/** to the
                                                // public internet; internal scrape is on the docker
                                                // network (zeromail-prometheus →
                                                // zeromail-api:8080).
                                                "/actuator/prometheus",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/login/oauth2/**",
                                                "/oauth2/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2Login(
                        oauth2Login ->
                                oauth2Login
                                        .successHandler(successHandler)
                                        .failureHandler(failureHandler)
                                        .authorizationEndpoint(
                                                authorizationEndpoint ->
                                                        authorizationEndpoint
                                                                .authorizationRequestResolver(
                                                                        authRequestResolver)))
                .csrf(
                        csrf ->
                                csrf.spa()
                                        .ignoringRequestMatchers(
                                                "/login/oauth2/code/**", "/oauth2/callback/**"))
                .exceptionHandling(
                        exceptionHandling ->
                                exceptionHandling.defaultAuthenticationEntryPointFor(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                        API_REQUEST_MATCHER))
                .logout(
                        logout ->
                                // Logout MUST live under /api so the production reverse proxy
                                // (routes only /api, /oauth2, /login/oauth2 to Spring) forwards it
                                // to the backend. A top-level /logout fell through to Next.js → 404
                                // → "Không thể đăng xuất". Mirrors the admin chain's
                                // /api/admin/logout.
                                logout.logoutRequestMatcher(
                                                PathPatternRequestMatcher.withDefaults()
                                                        .matcher(HttpMethod.POST, "/api/logout"))
                                        .logoutSuccessHandler(
                                                new HttpStatusReturningLogoutSuccessHandler())
                                        .invalidateHttpSession(true)
                                        .deleteCookies("ZEROMAIL_SESSION", "JSESSIONID"))
                .sessionManagement(Customizer.withDefaults())
                .addFilterAfter(tenantFilter, AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    WebAuthnRelyingPartyOperations adminWebAuthnRelyingPartyOperations(
            PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository,
            UserCredentialRepository userCredentialRepository) {
        Webauthn4JRelyingPartyOperations relyingPartyOperations =
                new Webauthn4JRelyingPartyOperations(
                        publicKeyCredentialUserEntityRepository,
                        userCredentialRepository,
                        PublicKeyCredentialRpEntity.builder()
                                .id(adminWebAuthnRpId)
                                .name(adminWebAuthnRpName)
                                .build(),
                        adminWebAuthnAllowedOrigins);
        relyingPartyOperations.setCustomizeCreationOptions(
                creationOptionsBuilder ->
                        creationOptionsBuilder.authenticatorSelection(
                                AuthenticatorSelectionCriteria.builder()
                                        .residentKey(ResidentKeyRequirement.REQUIRED)
                                        .userVerification(UserVerificationRequirement.REQUIRED)
                                        .build()));
        relyingPartyOperations.setCustomizeRequestOptions(
                requestOptionsBuilder ->
                        requestOptionsBuilder.userVerification(
                                UserVerificationRequirement.REQUIRED));
        return relyingPartyOperations;
    }

    @Bean
    @Primary
    CookieSerializer cookieSerializer(
            @Qualifier("adminCookieSerializer") CookieSerializer adminCookieSerializer,
            @Qualifier("userCookieSerializer") CookieSerializer userCookieSerializer) {
        // see docs/ops/admin-interface-freeze.md §Spring Session API
        return new PathRoutingCookieSerializer(adminCookieSerializer, userCookieSerializer);
    }

    @Bean
    CookieSerializer adminCookieSerializer(
            @Value("${zero-mail.session.cookie.secure:true}") boolean secureCookie) {
        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        cookieSerializer.setCookieName("ZEROMAIL_ADMIN");
        cookieSerializer.setCookiePath("/");
        cookieSerializer.setSameSite("Lax");
        cookieSerializer.setUseHttpOnlyCookie(true);
        cookieSerializer.setUseSecureCookie(secureCookie);
        return cookieSerializer;
    }

    @Bean
    CookieSerializer userCookieSerializer(
            @Value("${zero-mail.session.cookie.secure:true}") boolean secureCookie) {
        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        cookieSerializer.setCookieName("ZEROMAIL_SESSION");
        cookieSerializer.setCookiePath("/");
        cookieSerializer.setSameSite("Lax");
        cookieSerializer.setUseHttpOnlyCookie(true);
        cookieSerializer.setUseSecureCookie(secureCookie);
        return cookieSerializer;
    }
}
