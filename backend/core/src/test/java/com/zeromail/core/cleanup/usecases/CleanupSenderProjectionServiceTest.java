package com.zeromail.core.cleanup.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import com.zeromail.core.cleanup.usecases.CleanupRecentInboxWorkingSetService.SenderWorkingSet;
import com.zeromail.core.cleanup.usecases.CleanupRecentInboxWorkingSetService.SenderWorkingSetDailyCount;
import com.zeromail.core.cleanup.usecases.CleanupRecentInboxWorkingSetService.WorkingSet;
import com.zeromail.core.support.PostgresContainerTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class CleanupSenderProjectionServiceTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired CleanupSenderProjectionService cleanupSenderProjectionService;

    @Test
    void workingSetDomainIsNormalizedBeforeSuppressionLookup() {
        UUID tenantId = seedTenant();
        seedSuppressedSenderDomain(tenantId, "example.test");
        Instant lastSeenAt = Instant.parse("2026-05-23T09:00:00Z");

        cleanupSenderProjectionService.upsertWorkingSet(
                tenantId,
                new WorkingSet(
                        List.of(
                                new SenderWorkingSet(
                                        "newsletter@example.test",
                                        " Example.TEST ",
                                        null,
                                        1,
                                        1,
                                        lastSeenAt,
                                        UnsubscribeMethod.ONE_CLICK,
                                        null,
                                        List.of("gmail-1"),
                                        "https://example.test/unsubscribe",
                                        null))));

        List<UnsubscribeCandidateProjection> candidates =
                cleanupSenderProjectionService.findCandidates(
                        tenantId,
                        Instant.parse("2026-05-01T00:00:00Z"),
                        Instant.parse("2026-05-24T00:00:00Z"),
                        50);

        assertThat(candidates).as("suppressed domain must exclude Gmail-filled rows").isEmpty();
        assertThat(persistedSenderDomain(tenantId, "newsletter@example.test"))
                .isEqualTo("example.test");
    }

    @Test
    void workingSetCountsAreStoredPerActivityDay() {
        UUID tenantId = seedTenant();
        Instant olderLastSeenAt = Instant.parse("2026-03-01T11:00:00Z");
        Instant recentLastSeenAt = Instant.parse("2026-05-23T09:00:00Z");

        cleanupSenderProjectionService.upsertWorkingSet(
                tenantId,
                new WorkingSet(
                        List.of(
                                new SenderWorkingSet(
                                        "newsletter@example.test",
                                        "example.test",
                                        null,
                                        3,
                                        2,
                                        recentLastSeenAt,
                                        UnsubscribeMethod.ONE_CLICK,
                                        null,
                                        List.of("gmail-1", "gmail-2", "gmail-3"),
                                        "https://example.test/unsubscribe",
                                        null,
                                        List.of(
                                                new SenderWorkingSetDailyCount(
                                                        LocalDate.parse("2026-03-01"),
                                                        2,
                                                        1,
                                                        olderLastSeenAt),
                                                new SenderWorkingSetDailyCount(
                                                        LocalDate.parse("2026-05-23"),
                                                        1,
                                                        1,
                                                        recentLastSeenAt))))));

        List<UnsubscribeCandidateProjection> recentCandidates =
                cleanupSenderProjectionService.findCandidates(
                        tenantId,
                        Instant.parse("2026-05-20T00:00:00Z"),
                        Instant.parse("2026-05-24T00:00:00Z"),
                        50);
        List<UnsubscribeCandidateProjection> fullWindowCandidates =
                cleanupSenderProjectionService.findCandidates(
                        tenantId,
                        Instant.parse("2026-03-01T00:00:00Z"),
                        Instant.parse("2026-05-24T00:00:00Z"),
                        50);

        assertThat(recentCandidates).hasSize(1);
        assertThat(recentCandidates.getFirst().messageCount()).isEqualTo(1);
        assertThat(recentCandidates.getFirst().readMessageCount()).isEqualTo(1);
        assertThat(fullWindowCandidates).hasSize(1);
        assertThat(fullWindowCandidates.getFirst().messageCount()).isEqualTo(3);
        assertThat(fullWindowCandidates.getFirst().readMessageCount()).isEqualTo(2);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "cleanup-sender-projection-" + tenantId);
        return tenantId;
    }

    private void seedSuppressedSenderDomain(UUID tenantId, String senderDomain) {
        jdbcTemplate.update(
                """
                        insert into sender_suppression(
                            id, tenant_id, sender_email, sender_domain, reason, created_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                tenantId,
                null,
                senderDomain,
                "manual",
                java.sql.Timestamp.from(Instant.now()));
    }

    private String persistedSenderDomain(UUID tenantId, String senderEmail) {
        return jdbcTemplate.queryForObject(
                """
                        select sender_domain
                        from cleanup_sender_projection
                        where tenant_id = ? and sender_email = ?
                        """,
                String.class,
                tenantId,
                senderEmail);
    }
}
