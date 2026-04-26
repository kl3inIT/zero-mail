package com.zeromail.api.config;

import java.time.Clock;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Spring Data JPA auditing wiring for {@link com.zeromail.core.shared.persistence.AbstractAuditableEntity}.
 *
 * <p><b>Implements decision D-A4.</b> The {@link DateTimeProvider} named
 * {@code "appDateTimeProvider"} delegates to the project {@link Clock} bean so tests can
 * inject {@code Clock.fixed(...)} for deterministic timestamps. Production uses
 * {@code Clock.systemUTC()}.
 *
 * <p><b>Why a Clock-backed DateTimeProvider (not pure Hibernate {@code @PreUpdate}):</b>
 * {@code @PreUpdate} has no injection point for a clock (it's a static lifecycle callback),
 * forcing tests to either mock {@code Instant.now()} statically or rely on wall-clock time
 * with sleep-based assertions. Spring Data's {@link DateTimeProvider} interface is the
 * canonical override seam.
 *
 * <p><b>Why no DB trigger:</b> in-memory test divergence (H2/Postgres trigger differences)
 * and missing test-side determinism. The Clock-bean pattern is consistent with future
 * billing-ledger and triage code that will need {@code Clock} injection for time-window
 * arithmetic.
 *
 * <p><b>D-A4 explicit: NO {@code AuditorAware<UUID>} bean is registered.</b> Row-level user
 * identity is forbidden by the project privacy constraint until the Phase 6 audit-log table
 * lands. Adding {@code @CreatedBy} / {@code @LastModifiedBy} to
 * {@link com.zeromail.core.shared.persistence.AbstractAuditableEntity} is therefore
 * prohibited at this phase.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "appDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(name = "appDateTimeProvider")
    DateTimeProvider appDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
