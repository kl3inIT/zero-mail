package com.zeromail.core.gmail.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wave 0 RED scaffold — references {@code GmailConnectionService.upsert(...)} which does NOT exist
 * yet (Plan 02 Task 1 lands it). Compile fails on the {@code service.upsert} call until then.
 *
 * <p>Locks D-A4 (CONTEXT.md): single-row-per-tenant idempotency, version increments on re-grant,
 * scopes/token bytes round-trip cleanly, status transitions {@code DISCONNECTED → CONNECTED} on
 * re-grant and clears {@code disconnectedAt}.
 *
 * <p>Pattern: {@link PostgresContainerTest} (Phase 1.2.1 P03 standard) — real Hibernate round-trip
 * with {@code @TenantId} discriminator + Liquibase audit columns. Tenant binding MUST happen before
 * the JPA session opens (mirrors {@code OAuthProvisioningService.createTenantAndUser} discipline).
 */
class GmailConnectionServiceUpsertTest extends PostgresContainerTest {

    @Autowired GmailConnectionService service;
    @Autowired GmailConnectionRepository repo;
    @Autowired JdbcTemplate jdbc;

    @Test
    void upsert_insertsNewRowOnFirstCall() {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);

        byte[] envelope = new byte[] {1, 2, 3, 4};
        // Hibernate @TenantId filter applies on every JPA read — bind ScopedValue around
        // BOTH the upsert call AND the assertion-side findByTenantId so the discriminator
        // matches (Pitfall 6 / FND-05). Plan 02 SUMMARY documented this scaffold-repair need.
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            service.upsert(
                                    tenantId,
                                    "alpha@example.com",
                                    "https://www.googleapis.com/auth/gmail.modify",
                                    envelope);

                            var saved = repo.findByTenantId(tenantId);
                            assertThat(saved).isPresent();
                            GmailConnectionEntity row = saved.get();
                            assertThat(row.getStatus()).isEqualTo(GmailConnectionStatus.CONNECTED);
                            assertThat(row.getGoogleEmail()).isEqualTo("alpha@example.com");
                            assertThat(row.getRefreshTokenEncrypted()).containsExactly(1, 2, 3, 4);
                            assertThat(row.getScopesGranted())
                                    .isEqualTo("https://www.googleapis.com/auth/gmail.modify");
                            assertThat(row.getConnectedAt()).isNotNull();
                        });
    }

    @Test
    void upsert_updatesExistingRowOnSecondCall_singleRow() {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);

        // Wrap both upserts AND assertion-side reads inside ScopedValue so the @TenantId
        // filter resolves correctly on every JPA query path.
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            service.upsert(
                                    tenantId,
                                    "beta@example.com",
                                    "https://www.googleapis.com/auth/gmail.modify",
                                    new byte[] {9, 9, 9});
                            service.upsert(
                                    tenantId,
                                    "beta@example.com",
                                    "https://www.googleapis.com/auth/gmail.modify https://www.googleapis.com/auth/gmail.labels",
                                    new byte[] {8, 8, 8});

                            assertThat(repo.findAll())
                                    .as(
                                            "Single-row-per-tenant invariant — second upsert MUST update in place")
                                    .hasSize(1);

                            GmailConnectionEntity row = repo.findByTenantId(tenantId).orElseThrow();
                            assertThat(row.getRefreshTokenEncrypted()).containsExactly(8, 8, 8);
                            assertThat(row.getScopesGranted())
                                    .contains("gmail.modify")
                                    .contains("gmail.labels");
                            assertThat(row.getVersion())
                                    .as("Optimistic locking version must increment on update")
                                    .isGreaterThan(0);
                        });
    }

    @Test
    void upsert_resetsDisconnectedAtAndStatus() {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            service.upsert(
                                    tenantId,
                                    "gamma@example.com",
                                    "https://www.googleapis.com/auth/gmail.modify",
                                    new byte[] {1});
                            service.disconnect(tenantId);

                            GmailConnectionEntity afterDisconnect =
                                    repo.findByTenantId(tenantId).orElseThrow();
                            assertThat(afterDisconnect.getStatus())
                                    .isEqualTo(GmailConnectionStatus.DISCONNECTED);
                            assertThat(afterDisconnect.getDisconnectedAt()).isNotNull();

                            // Re-grant.
                            service.upsert(
                                    tenantId,
                                    "gamma@example.com",
                                    "https://www.googleapis.com/auth/gmail.modify",
                                    new byte[] {2});

                            GmailConnectionEntity afterReGrant =
                                    repo.findByTenantId(tenantId).orElseThrow();
                            assertThat(afterReGrant.getStatus())
                                    .isEqualTo(GmailConnectionStatus.CONNECTED);
                            assertThat(afterReGrant.getDisconnectedAt())
                                    .as(
                                            "D-A4: re-grant must clear disconnectedAt so status reflects the new CONNECTED state")
                                    .isNull();
                        });
    }

    @Test
    void updateMailboxProfile_updatesOnlyMatchingMailbox() {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        UUID primaryMailboxId =
                insertMailbox(
                        tenantId, "primary@example.test", GmailConnectionStatus.CONNECTED, true);
        UUID secondaryMailboxId =
                insertMailbox(
                        tenantId, "secondary@example.test", GmailConnectionStatus.CONNECTED, false);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            Optional<UUID> updatedMailboxId =
                                    service.updateMailboxProfile(
                                            tenantId,
                                            "secondary@example.test",
                                            "Secondary User",
                                            "https://lh3.googleusercontent.com/secondary");

                            assertThat(updatedMailboxId).contains(secondaryMailboxId);
                            GmailConnectionEntity primaryMailbox =
                                    repo.findById(primaryMailboxId).orElseThrow();
                            GmailConnectionEntity secondaryMailbox =
                                    repo.findById(secondaryMailboxId).orElseThrow();
                            assertThat(primaryMailbox.getGoogleProfileName()).isNull();
                            assertThat(primaryMailbox.getGoogleProfilePictureUrl()).isNull();
                            assertThat(secondaryMailbox.getGoogleProfileName())
                                    .isEqualTo("Secondary User");
                            assertThat(secondaryMailbox.getGoogleProfilePictureUrl())
                                    .isEqualTo("https://lh3.googleusercontent.com/secondary");
                        });
    }

    private void seedTenant(UUID tenantId) {
        // Pattern lifted from OnboardingStepPersistenceTest line 56 — JdbcTemplate is allowed
        // (TenantIsolationArchTests bans EntityManager.createNativeQuery only).
        jdbc.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "test-" + tenantId);
    }

    private UUID insertMailbox(
            UUID tenantId,
            String googleEmail,
            GmailConnectionStatus gmailConnectionStatus,
            boolean primary) {
        UUID gmailConnectionId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO gmail_connections(id, tenant_id, google_email, status, is_primary)
                VALUES (?, ?, ?, ?, ?)
                """,
                gmailConnectionId,
                tenantId,
                googleEmail,
                gmailConnectionStatus.name(),
                primary);
        return gmailConnectionId;
    }
}
