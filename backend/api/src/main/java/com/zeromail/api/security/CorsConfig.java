package com.zeromail.api.security;

import com.zeromail.api.config.ApiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * JHipster-style CORS wiring: keep the policy property-driven and let Spring Security consume the
 * {@link UrlBasedCorsConfigurationSource} via {@code http.cors()}.
 */
@Configuration
@Profile("!test")
public class CorsConfig {

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(ApiProperties properties) {
        var cors = properties.cors();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedOriginPatterns(cors.allowedOriginPatterns());
        config.setAllowedMethods(cors.allowedMethods());
        config.setAllowedHeaders(cors.allowedHeaders());
        config.setExposedHeaders(cors.exposedHeaders());
        config.setAllowCredentials(cors.allowCredentials());
        config.setMaxAge(cors.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
