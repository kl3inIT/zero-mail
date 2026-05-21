package com.zeromail.worker.billing;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires ShedLock through the Liquibase-managed shedlock table.
 *
 * <p>{@code usingDbTime()} keeps lock timestamps on the database clock, avoiding drift between
 * worker nodes.
 *
 * <p>Gated on {@code zero-mail.scheduling.enabled} (default ON). The {@code test} profile sets this
 * to {@code false} so {@code @Scheduled} worker jobs (Gmail history processor, watch scheduler,
 * credit reserve watchdog, drift detection, digest dispatch) do not fire in test JVMs.
 */
@Configuration
@ConditionalOnProperty(
        name = "zero-mail.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
