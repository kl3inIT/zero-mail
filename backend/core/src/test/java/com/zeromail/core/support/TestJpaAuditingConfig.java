package com.zeromail.core.support;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Test-only mirror of {@code com.zeromail.api.config.JpaAuditingConfig} for {@link
 * ZeroMailCoreTestApplication}.
 *
 * <p>The production {@code JpaAuditingConfig} lives in {@code backend/api} so the {@code
 * com.zeromail.core} scan base used by {@link ZeroMailCoreTestApplication} does not pick it up.
 * Without {@code @EnableJpaAuditing} bound to a {@link DateTimeProvider}, Hibernate issues INSERT
 * statements with explicit {@code NULL} for the {@code @CreatedDate} / {@code @LastModifiedDate}
 * fields on {@code com.zeromail.core.shared.persistence.AbstractAuditableEntity} — which then
 * collides with the {@code NOT NULL} audit columns and the per-row {@code defaultValueComputed:
 * now()} does NOT apply (DB defaults only fill columns ABSENT from the INSERT, not those bound to
 * NULL).
 *
 * <p>Mirrors {@code Clock.systemUTC()} from production. Tests that need deterministic time can
 * replace the clock bean with {@code Clock.fixed(...)} via a localized {@code @TestConfiguration}.
 *
 * <p>Picked up by {@link ZeroMailCoreTestApplication}'s {@code scanBasePackages =
 * "com.zeromail.core"} because this class lives under {@code com.zeromail.core.support}.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "appDateTimeProvider")
public class TestJpaAuditingConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(name = "appDateTimeProvider")
    DateTimeProvider appDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
