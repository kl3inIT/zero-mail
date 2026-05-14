package com.zeromail.api.security;

import com.google.auth.oauth2.TokenVerifier;
import com.zeromail.api.config.ZeroMailApiProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class PubSubSecurityConfig {

    @Bean
    public TokenVerifier pubsubOidcTokenVerifier(ZeroMailApiProperties properties) {
        ZeroMailApiProperties.PubSubProperties pubsubProperties = properties.gmail().pubsub();
        return TokenVerifier.newBuilder()
                .setAudience(pubsubProperties.pushAudienceUrl())
                .setIssuer("https://accounts.google.com")
                .setCertificatesLocation(pubsubProperties.oidcCertificatesUrl())
                .build();
    }

    @Bean
    PubSubOidcAuthFilter pubSubOidcAuthFilter(
            ZeroMailApiProperties properties, TokenVerifier tokenVerifier) {
        ZeroMailApiProperties.PubSubProperties pubsubProperties = properties.gmail().pubsub();
        return new PubSubOidcAuthFilter(pubsubProperties.saPrincipalEmail(), tokenVerifier);
    }

    @Bean
    FilterRegistrationBean<PubSubOidcAuthFilter> pubSubOidcAuthFilterRegistration(
            PubSubOidcAuthFilter filter) {
        FilterRegistrationBean<PubSubOidcAuthFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain pubSubFilterChain(HttpSecurity http, PubSubOidcAuthFilter oidcFilter) {
        return http.securityMatcher("/internal/pubsub/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(oidcFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
