package com.zeromail.worker.notification.config;

import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DigestDispatchClockConfig {

    @Bean
    @ConditionalOnMissingBean(Supplier.class)
    Supplier<Instant> currentInstant() {
        return Instant::now;
    }
}
