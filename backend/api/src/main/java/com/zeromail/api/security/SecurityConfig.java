package com.zeromail.api.security;

import com.zeromail.core.admin.auth.usecases.AdminUserDetailsService;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
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
@Profile("!test")
public class SecurityConfig {

    private static final PathPatternRequestMatcher API_REQUEST_MATCHER =
            PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    private static final PathPatternRequestMatcher ADMIN_REQUEST_MATCHER =
            PathPatternRequestMatcher.withDefaults().matcher("/api/admin/**");

    @Bean
    @Order(1)
    SecurityFilterChain adminChain(
            HttpSecurity http,
            AdminBindingFilter adminBindingFilter,
            AdminResponseBodyBanFilter adminResponseBodyBanFilter,
            AdminUserDetailsService adminUserDetailsService)
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
                                webAuthn.rpName("Zero Mail Admin")
                                        .rpId("admin.zeromail.com")
                                        .allowedOrigins("https://admin.zeromail.com")
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
                .sessionManagement(Customizer.withDefaults())
                .addFilterAfter(adminResponseBodyBanFilter, AuthorizationFilter.class)
                .addFilterAfter(adminBindingFilter, AdminResponseBodyBanFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain chain(
            HttpSecurity http,
            TenantBindingFilter tenantFilter,
            GoogleOAuthSuccessHandler successHandler,
            LoginRedirectAuthenticationFailureHandler failureHandler,
            GoogleAuthorizationRequestResolver authRequestResolver) {
        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(
                        authorizationRequests ->
                                authorizationRequests
                                        .requestMatchers(
                                                "/login",
                                                "/actuator/health",
                                                "/actuator/health/**",
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
                                .id("admin.zeromail.com")
                                .name("Zero Mail Admin")
                                .build(),
                        Set.of("https://admin.zeromail.com", "http://localhost:5174"));
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
    CookieSerializer adminCookieSerializer() {
        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        cookieSerializer.setCookieName("SESSION_ADMIN");
        cookieSerializer.setCookiePath("/");
        cookieSerializer.setSameSite("Lax");
        cookieSerializer.setUseHttpOnlyCookie(true);
        cookieSerializer.setUseSecureCookie(false);
        return cookieSerializer;
    }

    @Bean
    CookieSerializer userCookieSerializer() {
        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        cookieSerializer.setCookieName("SESSION_USER");
        cookieSerializer.setCookiePath("/");
        cookieSerializer.setSameSite("Lax");
        cookieSerializer.setUseHttpOnlyCookie(true);
        cookieSerializer.setUseSecureCookie(false);
        return cookieSerializer;
    }
}
