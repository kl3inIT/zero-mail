package com.zeromail.core.inbox.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.inbox.domain.InboxProjectionDataSource;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test for the projection read path. We seed rows via the production write service (so
 * encryption + sender hash flow exactly mirrors prod), then assert the read service decrypts back
 * to the same plaintext, paginates correctly, filters by {@code inbox_state} and {@code
 * expires_at}, and isolates tenants.
 *
 * <p>Wave 0 verifies the standalone DB path. The orchestrator (Wave 1) wraps these results with the
 * fallback logic; nothing in this test exercises that.
 */
class InboxProjectionReadServiceTest extends PostgresContainerTest {

    @Autowired InboxProjectionReadService inboxProjectionReadService;
    @Autowired InboxProjectionWriteService inboxProjectionWriteService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void empty_projection_returns_empty_page_with_projection_data_source() {
        UUID tenantId = seedTenant();

        InboxProjectionPage page =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxProjectionReadService.fetchInboxPage(tenantId, null, 20));

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.dataSource()).isEqualTo(InboxProjectionDataSource.PROJECTION);
    }

    @Test
    void fetch_decrypts_fields_and_synthesizes_from_header_with_display_name() {
        UUID tenantId = seedTenant();
        String gmailMessageId = "190000000000bb01";
        Instant receivedAt = Instant.parse("2026-06-01T10:00:00Z");
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                inboxProjectionWriteService.upsert(
                                        new InboxProjectionUpsertCommand(
                                                tenantId,
                                                gmailMessageId,
                                                "thread-xyz",
                                                "alice@example.com",
                                                "Alice Wonderland",
                                                "Welcome to Zero Mail",
                                                "Hi, here is a snippet preview...",
                                                false,
                                                receivedAt,
                                                List.of("INBOX", "UNREAD"),
                                                12345L)));

        InboxProjectionPage page =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxProjectionReadService.fetchInboxPage(tenantId, null, 20));

        assertThat(page.items()).hasSize(1);
        InboxProjectionMessage item = page.items().get(0);
        assertThat(item.gmailMessageId()).isEqualTo(gmailMessageId);
        assertThat(item.gmailThreadId()).isEqualTo("thread-xyz");
        assertThat(item.subject()).isEqualTo("Welcome to Zero Mail");
        assertThat(item.snippet()).isEqualTo("Hi, here is a snippet preview...");
        assertThat(item.from()).isEqualTo("Alice Wonderland <alice@example.com>");
        assertThat(item.receivedAt()).isEqualTo(receivedAt);
        assertThat(item.unread()).isTrue();
        assertThat(item.hasAttachment()).isFalse();
        assertThat(item.labelIds()).containsExactlyInAnyOrder("INBOX", "UNREAD");
        assertThat(item.to()).isEmpty();
        assertThat(item.cc()).isEmpty();
        assertThat(item.labels()).isEmpty();
        assertThat(page.dataSource()).isEqualTo(InboxProjectionDataSource.PROJECTION);
    }

    @Test
    void from_header_falls_back_to_bare_email_when_display_name_absent() {
        UUID tenantId = seedTenant();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                inboxProjectionWriteService.upsert(
                                        new InboxProjectionUpsertCommand(
                                                tenantId,
                                                "190000000000bb02",
                                                "thread-xyz",
                                                "bob@example.com",
                                                null,
                                                null,
                                                null,
                                                false,
                                                Instant.parse("2026-06-01T10:00:00Z"),
                                                List.of("INBOX"),
                                                100L)));

        InboxProjectionPage page =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxProjectionReadService.fetchInboxPage(tenantId, null, 20));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).from()).isEqualTo("bob@example.com");
    }

    @Test
    void from_header_quotes_display_name_with_special_characters() {
        UUID tenantId = seedTenant();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                inboxProjectionWriteService.upsert(
                                        new InboxProjectionUpsertCommand(
                                                tenantId,
                                                "190000000000bb03",
                                                "thread-xyz",
                                                "team@example.com",
                                                "Acme, Inc.",
                                                null,
                                                null,
                                                false,
                                                Instant.parse("2026-06-01T10:00:00Z"),
                                                List.of("INBOX"),
                                                100L)));

        InboxProjectionPage page =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxProjectionReadService.fetchInboxPage(tenantId, null, 20));

        assertThat(page.items().get(0).from()).isEqualTo("\"Acme, Inc.\" <team@example.com>");
    }

    @Test
    void rows_returned_in_descending_received_at_then_descending_message_id() {
        UUID tenantId = seedTenant();
        Instant olderReceivedAt = Instant.parse("2026-06-01T08:00:00Z");
        Instant newerReceivedAt = Instant.parse("2026-06-01T10:00:00Z");
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            seedRow(tenantId, "190000000000aa01", olderReceivedAt);
                            seedRow(tenantId, "190000000000aa02", newerReceivedAt);
                            seedRow(tenantId, "190000000000aa03", newerReceivedAt);
                        });

        InboxProjectionPage page =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxProjectionReadService.fetchInboxPage(tenantId, null, 20));

        assertThat(page.items())
                .extracting(InboxProjectionMessage::gmailMessageId)
                .containsExactly("190000000000aa03", "190000000000aa02", "190000000000aa01");
    }

    @Test
    void pagination_returns_next_page_via_keyset_cursor() {
        UUID tenantId = seedTenant();
        Instant baseTime = Instant.parse("2026-06-01T10:00:00Z");
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            for (int messageIndex = 0; messageIndex < 5; messageIndex++) {
                                seedRow(
                                        tenantId,
                                        String.format("19000000000ab%03d", messageIndex),
                                        baseTime.minus(Duration.ofMinutes(messageIndex)));
                            }
                        });

        InboxProjectionPage firstPage =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxProjectionReadService.fetchInboxPage(tenantId, null, 2));

        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.items().get(0).gmailMessageId()).isEqualTo("19000000000ab000");
        assertThat(firstPage.items().get(1).gmailMessageId()).isEqualTo("19000000000ab001");
        assertThat(firstPage.nextCursor()).isNotNull();

        InboxProjectionPage secondPage =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        inboxProjectionReadService.fetchInboxPage(
                                                tenantId, firstPage.nextCursor(), 2));

        assertThat(secondPage.items()).hasSize(2);
        assertThat(secondPage.items().get(0).gmailMessageId()).isEqualTo("19000000000ab002");
        assertThat(secondPage.items().get(1).gmailMessageId()).isEqualTo("19000000000ab003");
        assertThat(secondPage.nextCursor()).isNotNull();

        InboxProjectionPage thirdPage =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        inboxProjectionReadService.fetchInboxPage(
                                                tenantId, secondPage.nextCursor(), 2));

        assertThat(thirdPage.items()).hasSize(1);
        assertThat(thirdPage.items().get(0).gmailMessageId()).isEqualTo("19000000000ab004");
        assertThat(thirdPage.nextCursor()).isNull();
    }

    @Test
    void out_of_inbox_rows_are_excluded_from_the_page() {
        UUID tenantId = seedTenant();
        Instant receivedAt = Instant.parse("2026-06-01T10:00:00Z");
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            inboxProjectionWriteService.upsert(
                                    new InboxProjectionUpsertCommand(
                                            tenantId,
                                            "190000000000aa10",
                                            "thread-archived",
                                            "carol@example.com",
                                            null,
                                            null,
                                            null,
                                            false,
                                            receivedAt,
                                            List.of("Label_42"),
                                            100L));
                            inboxProjectionWriteService.upsert(
                                    new InboxProjectionUpsertCommand(
                                            tenantId,
                                            "190000000000aa11",
                                            "thread-still-inbox",
                                            "dave@example.com",
                                            null,
                                            null,
                                            null,
                                            false,
                                            receivedAt,
                                            List.of("INBOX"),
                                            200L));
                        });

        InboxProjectionPage page =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxProjectionReadService.fetchInboxPage(tenantId, null, 20));

        assertThat(page.items())
                .extracting(InboxProjectionMessage::gmailMessageId)
                .containsExactly("190000000000aa11");
    }

    @Test
    void rows_past_expires_at_are_excluded_from_the_page() {
        UUID tenantId = seedTenant();
        Instant receivedAt = Instant.parse("2026-06-01T10:00:00Z");
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> seedRow(tenantId, "190000000000aa20", receivedAt));
        // Forcibly age out the expires_at to simulate stale row past TTL.
        jdbcTemplate.update(
                "UPDATE gmail_inbox_projection SET expires_at = NOW() - INTERVAL '1 day'"
                        + " WHERE tenant_id = ? AND gmail_message_id = ?",
                tenantId,
                "190000000000aa20");
        // Seed a second fresh row so the page is not unconditionally empty.
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> seedRow(tenantId, "190000000000aa21", receivedAt));

        InboxProjectionPage page =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> inboxProjectionReadService.fetchInboxPage(tenantId, null, 20));

        assertThat(page.items())
                .extracting(InboxProjectionMessage::gmailMessageId)
                .containsExactly("190000000000aa21");
    }

    @Test
    void tenant_isolation_other_tenants_rows_are_not_returned() {
        UUID tenantA = seedTenant();
        UUID tenantB = seedTenant();
        Instant receivedAt = Instant.parse("2026-06-01T10:00:00Z");
        ScopedValue.where(TenantContext.TENANT, tenantA.toString())
                .run(() -> seedRow(tenantA, "190000000000aa30", receivedAt));
        ScopedValue.where(TenantContext.TENANT, tenantB.toString())
                .run(() -> seedRow(tenantB, "190000000000aa31", receivedAt));

        InboxProjectionPage pageForA =
                ScopedValue.where(TenantContext.TENANT, tenantA.toString())
                        .call(() -> inboxProjectionReadService.fetchInboxPage(tenantA, null, 20));

        assertThat(pageForA.items())
                .extracting(InboxProjectionMessage::gmailMessageId)
                .containsExactly("190000000000aa30");
    }

    private void seedRow(UUID tenantId, String gmailMessageId, Instant receivedAt) {
        inboxProjectionWriteService.upsert(
                new InboxProjectionUpsertCommand(
                        tenantId,
                        gmailMessageId,
                        "thread-" + gmailMessageId,
                        gmailMessageId + "@example.com",
                        "Sender " + gmailMessageId,
                        "Subject " + gmailMessageId,
                        "Snippet " + gmailMessageId,
                        false,
                        receivedAt,
                        List.of("INBOX", "UNREAD"),
                        100L));
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        return tenantId;
    }
}
