package com.zeromail.worker.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables audit timestamp population for core JPA entities persisted by the worker. */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "appDateTimeProvider")
public class WorkerJpaAuditingConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(name = "appDateTimeProvider")
    DateTimeProvider appDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
