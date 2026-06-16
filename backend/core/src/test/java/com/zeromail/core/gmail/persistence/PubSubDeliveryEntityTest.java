package com.zeromail.core.gmail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class PubSubDeliveryEntityTest extends PostgresContainerTest {

    private static final UUID GMAIL_CONNECTION_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    @Autowired JdbcTemplate jdbc;
    @Autowired PubSubDeliveryRepository deliveries;

    @Test
    void insertAndRead_roundtrip() {
        UUID tenantId = seedTenant();
        UUID id = UUID.randomUUID();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                deliveries.saveAndFlush(
                                        new PubSubDeliveryEntity(
                                                id,
                                                tenantId,
                                                GMAIL_CONNECTION_ID,
                                                "pubsub-1",
                                                123L,
                                                "{\"messageId\":\"pubsub-1\"}")));

        PubSubDeliveryEntity found =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> deliveries.findById(id).orElseThrow());
        assertThat(found.getTenantId()).isEqualTo(tenantId);
        assertThat(found.getPubSubMessageId()).isEqualTo("pubsub-1");
        assertThat(found.getHistoryId()).isEqualTo(123L);
        assertThat(found.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void uniqueConstraint_preventsduplicateMessageId() {
        UUID tenantId = seedTenant();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            deliveries.saveAndFlush(
                                    new PubSubDeliveryEntity(
                                            UUID.randomUUID(),
                                            tenantId,
                                            GMAIL_CONNECTION_ID,
                                            "pubsub-unique",
                                            1L,
                                            "{}"));
                            assertThatThrownBy(
                                            () ->
                                                    deliveries.saveAndFlush(
                                                            new PubSubDeliveryEntity(
                                                                    UUID.randomUUID(),
                                                                    tenantId,
                                                                    GMAIL_CONNECTION_ID,
                                                                    "pubsub-unique",
                                                                    2L,
                                                                    "{}")))
                                    .isInstanceOf(DataIntegrityViolationException.class);
                        });
    }

    @Test
    void atomicClaimPendingBatch_updatesPendingRowsToProcessing() {
        UUID tenantId = seedTenant();
        insertDelivery(tenantId, "pending-1", "PENDING", 0, null);
        insertDelivery(tenantId, "pending-2", "PENDING", 0, null);
        insertDelivery(tenantId, "pending-3", "PENDING", 0, null);

        List<PubSubDeliveryEntity> claimed =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> deliveries.claimPendingBatchForTenant(tenantId, 2, 300));

        assertThat(claimed).hasSize(2);
        Long processing =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pubsub_delivery WHERE tenant_id = ? AND status = 'PROCESSING'",
                        Long.class,
                        tenantId);
        assertThat(processing).isEqualTo(2L);
        Long attempts =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pubsub_delivery WHERE tenant_id = ? AND attempts = 1 AND locked_until > NOW()",
                        Long.class,
                        tenantId);
        assertThat(attempts).isEqualTo(2L);
    }

    @Test
    void globalClaimPendingBatch_withoutTenantContext_claimsRowsForWorkerFanout() {
        UUID tenantId = seedTenant();
        insertDelivery(tenantId, "pending-global", "PENDING", 0, null);

        List<PubSubDeliveryEntity> claimed = deliveries.claimPendingBatch(1, 300);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().getTenantId()).isEqualTo(tenantId);
        assertThat(statusForMessage("pending-global")).isEqualTo("PROCESSING");
    }

    @Test
    void expiredProcessingRows_areReclaimedByClaimPendingBatch() {
        UUID tenantId = seedTenant();
        insertDelivery(
                tenantId, "expired-processing", "PROCESSING", 2, Instant.now().minusSeconds(60));

        List<PubSubDeliveryEntity> claimed =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> deliveries.claimPendingBatchForTenant(tenantId, 1, 300));

        assertThat(claimed).hasSize(1);
        Integer attempts =
                jdbc.queryForObject(
                        "SELECT attempts FROM pubsub_delivery WHERE tenant_id = ? AND pubsub_message_id = ?",
                        Integer.class,
                        tenantId,
                        "expired-processing");
        assertThat(attempts).isEqualTo(3);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        return tenantId;
    }

    private void insertDelivery(
            UUID tenantId, String messageId, String status, int attempts, Instant lockedUntil) {
        jdbc.update(
                """
                INSERT INTO pubsub_delivery(id, tenant_id, gmail_connection_id, pubsub_message_id, history_id, payload, status, attempts, locked_until)
                VALUES (?, ?, '00000000-0000-4000-8000-0000000000c1', ?, ?, '{}'::jsonb, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                messageId,
                10L,
                status,
                attempts,
                lockedUntil == null ? null : Timestamp.from(lockedUntil));
    }

    private String statusForMessage(String messageId) {
        return jdbc.queryForObject(
                "SELECT status FROM pubsub_delivery WHERE pubsub_message_id = ?",
                String.class,
                messageId);
    }
}
