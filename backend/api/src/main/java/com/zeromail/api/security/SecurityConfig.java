package com.zeromail.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@Profile("!test")
public class SecurityConfig {

    private static final PathPatternRequestMatcher API_REQUEST_MATCHER =
            PathPatternRequestMatcher.withDefaults().matcher("/api/**");

    @Bean
    @Order(3)
    SecurityFilterChain chain(
            HttpSecurity http,
            TenantBindingFilter tenantFilter,
            GoogleOAuthSuccessHandler successHandler,
            LoginRedirectAuthenticationFailureHandler failureHandler,
            GoogleAuthorizationRequestResolver authRequestResolver) {
        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
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
                        o ->
                                o.successHandler(successHandler)
                                        .failureHandler(failureHandler)
                                        .authorizationEndpoint(
                                                a ->
                                                        a.authorizationRequestResolver(
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
}
