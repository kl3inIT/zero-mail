package com.zeromail.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class PubSubSecurityConfig {

    @Bean
    PubSubOidcAuthFilter pubSubOidcAuthFilter(
            @Value("${pubsub.push-audience-url}") String audience,
            @Value("${pubsub.sa-principal-email}") String saEmail,
            @Value("${pubsub.oidc-certificates-url:https://www.googleapis.com/oauth2/v3/certs}") String certsUrl) {
        return new PubSubOidcAuthFilter(audience, saEmail, certsUrl);
    }

    @Bean
    FilterRegistrationBean<PubSubOidcAuthFilter> pubSubOidcAuthFilterRegistration(PubSubOidcAuthFilter filter) {
        FilterRegistrationBean<PubSubOidcAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain pubSubFilterChain(HttpSecurity http, PubSubOidcAuthFilter oidcFilter) throws Exception {
        return http
                .securityMatcher("/internal/pubsub/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(oidcFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
