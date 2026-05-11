package com.zeromail.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.worker.test.MockGmailHistoryServer;
import com.zeromail.worker.test.MockGmailHistoryServer.HistoryMessageResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class GmailHistoryProcessorTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired GmailHistoryProcessor processor;

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
    void processDelivery_insertsMailMessageObserved() {
        UUID tenantId = seedConnectedGmail("history-insert@example.test", 10L);
        seedDelivery(tenantId, "delivery-insert", 11L);
        gmail.stubHistoryList(
                10L, List.of(new HistoryMessageResponse("gmail-1", "thread-1", List.of(), null)));
        gmail.stubMessageMetadata("gmail-1", "thread-1", List.of("INBOX"), 1_700_000_000_000L);

        processor.tick();

        assertThat(count("mail_message_observed", tenantId)).isEqualTo(1L);
        assertThat(status("delivery-insert")).isEqualTo("PROCESSED");
    }

    @Test
    void processDelivery_history404_setsHistoryLost() {
        UUID tenantId = seedConnectedGmail("history-lost@example.test", 10L);
        seedDelivery(tenantId, "delivery-history-lost", 99L);
        gmail.stubHistoryList404();

        processor.tick();

        assertThat(gmailConnectionColumn(tenantId, "ingestion_health")).isEqualTo("HISTORY_LOST");
        assertThat(status("delivery-history-lost")).isEqualTo("PROCESSED");
        assertThat(gmailConnectionColumn(tenantId, "last_synced_history_id")).isEqualTo(99L);
    }

    @Test
    void processDelivery_idempotent_duplicateMessage() {
        UUID tenantId = seedConnectedGmail("history-dupe@example.test", 10L);
        seedDelivery(tenantId, "delivery-dupe-1", 11L);
        seedDelivery(tenantId, "delivery-dupe-2", 12L);
        gmail.stubHistoryList(
                10L,
                List.of(new HistoryMessageResponse("gmail-dupe", "thread-dupe", List.of(), null)));
        gmail.stubMessageMetadata(
                "gmail-dupe", "thread-dupe", List.of("INBOX"), 1_700_000_000_000L);

        processor.tick();
        processor.tick();

        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mail_message_observed WHERE tenant_id = ? AND gmail_message_id = ?",
                        Long.class,
                        tenantId,
                        "gmail-dupe");
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void processDelivery_readsAllHistoryPagesBeforeAdvancingPointer() {
        UUID tenantId = seedConnectedGmail("history-pages@example.test", 10L);
        seedDelivery(tenantId, "delivery-pages", 30L);
        gmail.stubHistoryListPage(
                10L,
                "page-2",
                List.of(
                        new HistoryMessageResponse(
                                "gmail-page-1", "thread-page-1", List.of(), null)));
        gmail.stubHistoryListPageToken(
                "page-2",
                11L,
                List.of(
                        new HistoryMessageResponse(
                                "gmail-page-2", "thread-page-2", List.of(), null)));
        gmail.stubMessageMetadata(
                "gmail-page-1", "thread-page-1", List.of("INBOX"), 1_700_000_000_000L);
        gmail.stubMessageMetadata(
                "gmail-page-2", "thread-page-2", List.of("INBOX"), 1_700_000_000_001L);

        processor.tick();

        assertThat(messageExists(tenantId, "gmail-page-1")).isTrue();
        assertThat(messageExists(tenantId, "gmail-page-2")).isTrue();
        assertThat(gmailConnectionColumn(tenantId, "last_synced_history_id")).isEqualTo(30L);
        assertThat(status("delivery-pages")).isEqualTo("PROCESSED");
    }

    @Test
    void processDelivery_scopedValueBound_perRow() {
        UUID tenantA = seedConnectedGmail("history-a@example.test", 10L);
        UUID tenantB = seedConnectedGmail("history-b@example.test", 20L);
        seedDelivery(tenantA, "delivery-a", 11L);
        seedDelivery(tenantB, "delivery-b", 21L);
        gmail.stubHistoryList(
                10L, List.of(new HistoryMessageResponse("gmail-a", "thread-a", List.of(), null)));
        gmail.stubHistoryList(
                20L, List.of(new HistoryMessageResponse("gmail-b", "thread-b", List.of(), null)));
        gmail.stubMessageMetadata("gmail-a", "thread-a", List.of("INBOX"), 1_700_000_000_000L);
        gmail.stubMessageMetadata("gmail-b", "thread-b", List.of("INBOX"), 1_700_000_000_001L);

        processor.tick();

        assertThat(count("mail_message_observed", tenantA)).isEqualTo(1L);
        assertThat(count("mail_message_observed", tenantB)).isEqualTo(1L);
        assertThat(messageExists(tenantA, "gmail-a")).isTrue();
        assertThat(messageExists(tenantB, "gmail-b")).isTrue();
        assertThat(messageExists(tenantB, "gmail-a")).isFalse();
    }

    @Test
    void processDelivery_invalidGrant_setsDisconnected() {
        UUID tenantId = seedConnectedGmail("invalid-grant@example.test", 10L);
        seedDelivery(tenantId, "delivery-invalid-grant", 11L);
        gmail.stubTokenInvalidGrant();

        processor.tick();

        assertThat(gmailConnectionColumn(tenantId, "status")).isEqualTo("DISCONNECTED");
        assertThat(status("delivery-invalid-grant")).isEqualTo("DEAD");
    }

    @Test
    void processDelivery_historyListMessageWithoutLabels_fetchesMetadataBeforeInboxFilter() {
        UUID tenantId = seedConnectedGmail("metadata-fetch@example.test", 10L);
        seedDelivery(tenantId, "delivery-metadata", 11L);
        gmail.stubHistoryList(
                10L,
                List.of(
                        new HistoryMessageResponse(
                                "gmail-metadata", "thread-metadata", List.of(), null)));
        gmail.stubMessageMetadata(
                "gmail-metadata", "thread-metadata", List.of("INBOX"), 1_700_000_000_000L);

        processor.tick();

        Long internalDate =
                jdbc.queryForObject(
                        "SELECT internal_date FROM mail_message_observed WHERE tenant_id = ? AND gmail_message_id = ?",
                        Long.class,
                        tenantId,
                        "gmail-metadata");
        assertThat(internalDate).isEqualTo(1_700_000_000_000L);
    }

    private UUID seedConnectedGmail(String email, long lastSyncedHistoryId) {
        UUID tenantId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        jdbc.update(
                """
                INSERT INTO gmail_connections(
                    id, tenant_id, google_email, status, refresh_token_encrypted, last_synced_history_id, ingestion_health
                )
                VALUES (?, ?, ?, 'CONNECTED', ?, ?, 'HEALTHY')
                """,
                UUID.randomUUID(),
                tenantId,
                email,
                encryptedRefreshToken(tenantId),
                lastSyncedHistoryId);
        return tenantId;
    }

    private void seedDelivery(UUID tenantId, String messageId, long historyId) {
        jdbc.update(
                """
                INSERT INTO pubsub_delivery(id, tenant_id, pubsub_message_id, history_id, payload, status)
                VALUES (?, ?, ?, ?, '{}'::jsonb, 'PENDING')
                """,
                UUID.randomUUID(),
                tenantId,
                messageId,
                historyId);
    }

    private Long count(String table, UUID tenantId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tenantId);
    }

    private boolean messageExists(UUID tenantId, String gmailMessageId) {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM mail_message_observed WHERE tenant_id = ? AND gmail_message_id = ?",
                        Long.class,
                        tenantId,
                        gmailMessageId);
        return count != null && count > 0;
    }

    private String status(String messageId) {
        return jdbc.queryForObject(
                "SELECT status FROM pubsub_delivery WHERE pubsub_message_id = ?",
                String.class,
                messageId);
    }

    private Object gmailConnectionColumn(UUID tenantId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM gmail_connections WHERE tenant_id = ?",
                Object.class,
                tenantId);
    }
}
