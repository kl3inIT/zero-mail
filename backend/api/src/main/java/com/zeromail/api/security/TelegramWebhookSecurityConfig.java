package com.zeromail.api.security;

import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class TelegramWebhookSecurityConfig {

    @Bean
    TelegramWebhookSecretFilter telegramWebhookSecretFilter(TelegramProperties telegramProperties) {
        return new TelegramWebhookSecretFilter(telegramProperties);
    }

    @Bean
    FilterRegistrationBean<TelegramWebhookSecretFilter> telegramWebhookSecretFilterRegistration(
            TelegramWebhookSecretFilter telegramWebhookSecretFilter) {
        FilterRegistrationBean<TelegramWebhookSecretFilter> registration =
                new FilterRegistrationBean<>(telegramWebhookSecretFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(2)
    SecurityFilterChain telegramWebhookFilterChain(
            HttpSecurity http, TelegramWebhookSecretFilter telegramWebhookSecretFilter) {
        return http.securityMatcher("/api/integrations/telegram/webhook")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorization ->
                                authorization
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/integrations/telegram/webhook")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        telegramWebhookSecretFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
