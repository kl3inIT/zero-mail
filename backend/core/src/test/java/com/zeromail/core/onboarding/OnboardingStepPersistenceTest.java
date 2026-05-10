package com.zeromail.core.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.onboarding.domain.OnboardingStep;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;

/**
 * Phase 1.2.1 WR-01 closure (D-C4): defends Pitfall 5 / T-01.2-E with a REAL Hibernate
 * round-trip plus a raw-column assertion that the {@code users.onboarding_step} column is
 * {@code character varying} and stores {@code OnboardingStep.id()} (== {@code name()})
 * verbatim. Catches an accidental {@code @Enumerated(EnumType.STRING)} → {@code (EnumType.ORDINAL)}
 * switch (the previous pure-JVM test would silently pass under that regression).
 *
 * <p>Pattern: extends {@link PostgresContainerTest} (NOT {@code @DataJpaTest}) per the
 * project standard — {@code @DataJpaTest} would skip {@code ZeroMailCoreTestApplication} and miss
 * the Liquibase audit-column changeset (007). 4 existing DB-touching tests follow this
 * pattern: {@code PreferredLanguageMigrationTest}, {@code GmailConnectionUniquenessTest},
 * {@code LiquibaseMigrationTest}, and {@code MultiTenantLeakIntegrationTest}.
 *
 * <p><b>Tenant binding:</b> {@code @TenantId} discriminator filter requires the
 * {@code TenantContext.TENANT} {@code ScopedValue} to be bound during persistence; we wrap
 * the persist call in {@code ScopedValue.where(...).run(...)}.
 *
 * <p><b>Native SQL note:</b> {@code TenantIsolationArchTests.no_native_sql} bans
 * {@code EntityManager.createNativeQuery} outside {@code ..persistence.lowlevel..}.
 * {@link JdbcTemplate} is allowed (rule only matches the EntityManager API per REVIEW WR-01
 * note line 109).
 */
class OnboardingStepPersistenceTest extends PostgresContainerTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository users;

    @ParameterizedTest
    @EnumSource(OnboardingStep.class)
    void each_onboarding_step_persists_as_id_string(OnboardingStep step) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Seed FK parent (tenants table) — pattern lifted from GmailConnectionUniquenessTest line 27.
        jdbc.update("INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId, "test-" + tenantId);

        // Persist a UserEntity at the given step under a bound TenantContext so Hibernate's
        // @TenantId filter binds the discriminator on insert.
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
            UserEntity u = new UserEntity(
                    userId,
                    tenantId,
                    "sub-" + step.id(),
                    "user-" + step.id().toLowerCase() + "@example.test");
            // advanceTo enforces the forward-only invariant via weight() (post-Task-1);
            // GMAIL_CONNECTED is the entry state (Phase 01.5 D-B1 simplification).
            u.advanceTo(step);
            users.saveAndFlush(u);
        });

        // 1. Raw column read via JdbcTemplate (allowed by ArchUnit).
        String rawValue = jdbc.queryForObject(
                "SELECT onboarding_step FROM users WHERE id = ?",
                String.class,
                userId);
        assertThat(rawValue)
                .as("Hibernate should persist OnboardingStep.%s as its id() string", step)
                .isEqualTo(step.id());

        // 2. id() == name() invariant (D-C2): paranoia check that the contract holds.
        assertThat(rawValue).isEqualTo(step.name());

        // 3. Column type is varchar (catches accidental ORDINAL switch — that would store integer).
        Map<String, Object> col = jdbc.queryForMap(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'users' AND column_name = 'onboarding_step'");
        assertThat(col).containsEntry("data_type", "character varying");
    }
}
