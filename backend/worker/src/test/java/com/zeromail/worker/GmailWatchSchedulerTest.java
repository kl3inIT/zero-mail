package com.zeromail.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.worker.test.MockGmailHistoryServer;

class GmailWatchSchedulerTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired GmailWatchScheduler scheduler;

    private MockGmailHistoryServer gmail;

    @BeforeEach
    void resetState() {
        jdbc.execute("DELETE FROM mail_message_observed");
        jdbc.execute("DELETE FROM pubsub_delivery");
        jdbc.execute("DELETE FROM gmail_connections");
        jdbc.execute("DELETE FROM tenants");
        gmail = GMAIL;
        gmail.reset();
        gmail.stubTokenSuccess();
    }

    @Test
    void register_nullExpiry_issuersWatch() {
        UUID tenantId = seedConnectedGmail(null, null, "HEALTHY", 0);
        gmail.stubWatchSuccess(200L, Instant.now().plusSeconds(604800).toEpochMilli());

        scheduler.tick();

        assertThat(connectionColumn(tenantId, "watch_history_id")).isEqualTo(200L);
        assertThat(connectionColumn(tenantId, "watch_expires_at")).isNotNull();
        assertThat(connectionColumn(tenantId, "watch_renewed_at")).isNotNull();
        assertThat(connectionColumn(tenantId, "ingestion_health")).isEqualTo("HEALTHY");
    }

    @Test
    void renew_expiryWithin24h_issuersWatch() {
        UUID tenantId = seedConnectedGmail(100L, Instant.now().plusSeconds(23 * 60 * 60), "HEALTHY", 0);
        gmail.stubWatchSuccess(201L, Instant.now().plusSeconds(604800).toEpochMilli());

        scheduler.tick();

        assertThat(connectionColumn(tenantId, "watch_history_id")).isEqualTo(201L);
        assertThat(connectionColumn(tenantId, "watch_renewed_at")).isNotNull();
    }

    @Test
    void tick_withoutTenantContext_processesGlobalRenewalScan() {
        UUID tenantId = seedConnectedGmail(null, null, "HEALTHY", 0);
        gmail.stubWatchSuccess(204L, Instant.now().plusSeconds(604800).toEpochMilli());

        scheduler.tick();

        assertThat(connectionColumn(tenantId, "watch_history_id")).isEqualTo(204L);
        assertThat(connectionColumn(tenantId, "ingestion_health")).isEqualTo("HEALTHY");
    }

    @Test
    void threeConsecutiveFailures_setsWatchUnhealthy() {
        UUID tenantId = seedConnectedGmail(null, null, "HEALTHY", 2);
        gmail.stubWatchFailure(503);

        scheduler.tick();

        assertThat(connectionColumn(tenantId, "ingestion_health")).isEqualTo("WATCH_UNHEALTHY");
    }

    @Test
    void watchRequest_inboxOnly_labelIds() {
        seedConnectedGmail(null, null, "HEALTHY", 0);
        gmail.stubWatchSuccess(202L, Instant.now().plusSeconds(604800).toEpochMilli());

        scheduler.tick();

        assertThat(gmail.lastWatchRequestBody()).contains("\"labelIds\":[\"INBOX\"]");
        assertThat(gmail.lastWatchRequestBody()).contains("\"labelFilterBehavior\":\"include\"");
    }

    @Test
    void renew_existingHistoryPointer_doesNotAdvanceLastSyncedHistoryId() {
        UUID tenantId = seedConnectedGmail(100L, Instant.now().plusSeconds(23 * 60 * 60), "HEALTHY", 0);
        seedPendingDelivery(tenantId, 110L);
        gmail.stubWatchSuccess(200L, Instant.now().plusSeconds(604800).toEpochMilli());

        scheduler.tick();

        assertThat(connectionColumn(tenantId, "last_synced_history_id")).isEqualTo(100L);
        assertThat(connectionColumn(tenantId, "watch_history_id")).isEqualTo(200L);
    }

    @Test
    void watchRenewal_historyLost_doesNotClearIngestionHealth() {
        UUID tenantId = seedConnectedGmail(100L, Instant.now().plusSeconds(23 * 60 * 60), "HISTORY_LOST", 0);
        gmail.stubWatchSuccess(203L, Instant.now().plusSeconds(604800).toEpochMilli());

        scheduler.tick();

        assertThat(connectionColumn(tenantId, "ingestion_health")).isEqualTo("HISTORY_LOST");
    }

    private UUID seedConnectedGmail(Long lastSyncedHistoryId, Instant watchExpiresAt, String ingestionHealth, int failures) {
        UUID tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, "tenant-" + tenantId);
        jdbc.update("""
                INSERT INTO gmail_connections(
                    id, tenant_id, google_email, status, refresh_token_encrypted, last_synced_history_id, watch_expires_at,
                    ingestion_health, watch_consecutive_failures
                )
                VALUES (?, ?, ?, 'CONNECTED', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, "watch-" + tenantId + "@example.test",
                encryptedRefreshToken(tenantId),
                lastSyncedHistoryId,
                watchExpiresAt == null ? null : Timestamp.from(watchExpiresAt),
                ingestionHealth,
                failures);
        return tenantId;
    }

    private void seedPendingDelivery(UUID tenantId, long historyId) {
        jdbc.update("""
                INSERT INTO pubsub_delivery(id, tenant_id, pubsub_message_id, history_id, payload, status)
                VALUES (?, ?, ?, ?, '{}'::jsonb, 'PENDING')
                """, UUID.randomUUID(), tenantId, "watch-delivery-" + historyId, historyId);
    }

    private Object connectionColumn(UUID tenantId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM gmail_connections WHERE tenant_id = ?",
                Object.class,
                tenantId);
    }
}
