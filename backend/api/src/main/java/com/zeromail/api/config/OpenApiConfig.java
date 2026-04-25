package com.zeromail.api.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenApiCustomizer phase1Info() {
        return api -> api.setInfo(new Info()
                .title("Zero Mail API")
                .version("0.1.0")
                .description("Phase 1 skeleton: auth + onboarding + tenant status + delete"));
    }
}
