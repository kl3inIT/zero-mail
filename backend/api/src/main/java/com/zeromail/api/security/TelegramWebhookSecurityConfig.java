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

    /**
     * Dedicated filter chain for the Telegram webhook endpoint only.
     *
     * <p>CSRF protection is intentionally disabled here. CSRF defends stateful, cookie/session
     * authenticated browser requests where the browser auto-attaches ambient credentials. This
     * endpoint is the opposite: a {@code STATELESS} machine-to-machine POST from Telegram's
     * servers, with no session and no cookies. Authentication is the shared {@code
     * X-Telegram-Bot-Api-Secret-Token} header validated in constant time by {@link
     * TelegramWebhookSecretFilter}; a forged cross-site request cannot supply that secret. Spring's
     * CSRF token would also break the integration because Telegram does not (and cannot) send one.
     * The main application chains keep {@code csrf().spa()} enabled — this carve-out is scoped to
     * the webhook path via {@code securityMatcher} and does not relax CSRF anywhere else.
     */
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
