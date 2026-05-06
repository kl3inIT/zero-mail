package com.zeromail.api.security.billing;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.zeromail.core.billing.service.SepayApiKeyVerifier;

@Configuration
public class BillingWebhookSecurityConfig {

  @Bean
  SepayApiKeyAuthFilter sepayApiKeyAuthFilter(SepayApiKeyVerifier verifier) {
    return new SepayApiKeyAuthFilter(verifier);
  }

  @Bean
  FilterRegistrationBean<SepayApiKeyAuthFilter> sepayApiKeyAuthFilterRegistration(
      SepayApiKeyAuthFilter sepayApiKeyAuthFilter) {
    FilterRegistrationBean<SepayApiKeyAuthFilter> registration =
        new FilterRegistrationBean<>(sepayApiKeyAuthFilter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  @Order(2)
  SecurityFilterChain sepayWebhookFilterChain(
      HttpSecurity http, SepayApiKeyAuthFilter sepayApiKeyAuthFilter) throws Exception {
    return http.securityMatcher("/api/billing/sepay/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            sessionManagementConfigurer ->
                sessionManagementConfigurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorizationRegistry -> authorizationRegistry.anyRequest().permitAll())
        .addFilterBefore(sepayApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
