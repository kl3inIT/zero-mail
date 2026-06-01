package com.zeromail.core.inbox.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.inbox.domain.EncryptedField;
import com.zeromail.core.inbox.domain.InboxState;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionEntity;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionId;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionRepository;
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
 * Integration test for the write service end-to-end: encrypt → UPSERT → decryptable round-trip
 * via the cipher, and idempotent re-observe semantics (inbox_state flips with the label set).
 */
class InboxProjectionWriteServiceTest extends PostgresContainerTest {

    @Autowired InboxProjectionWriteService inboxProjectionWriteService;
    @Autowired InboxProjectionCipher cipher;
    @Autowired GmailInboxProjectionRepository projectionRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void upsert_round_trips_through_cipher_and_persists_with_INBOX_state_for_inbox_labels() {
        UUID tenantId = seedTenant();
        String gmailMessageId = "190000000000bb01";
        Instant receivedAt = Instant.now().minus(Duration.ofMinutes(2));

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
                                                424242L)));

        GmailInboxProjectionEntity projection =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        projectionRepository
                                                .findById(
                                                        new GmailInboxProjectionId(
                                                                tenantId, gmailMessageId))
                                                .orElseThrow());

        assertThat(projection.getInboxState()).isEqualTo(InboxState.INBOX);
        assertThat(projection.isUnread()).isTrue();
        assertThat(projection.getSourceHistoryId()).isEqualTo(424242L);
        assertThat(projection.getLabelIds()).containsExactlyInAnyOrder("INBOX", "UNREAD");
        assertThat(projection.getExpiresAt()).isAfter(projection.getRefreshedAt());
        Duration ttlDelta =
                Duration.between(
                        projection.getRefreshedAt().plus(Duration.ofDays(90)),
                        projection.getExpiresAt());
        assertThat(ttlDelta.abs()).isLessThan(Duration.ofSeconds(2));

        // Round-trip the encrypted fields.
        assertThat(
                        cipher.decrypt(
                                projection.getSenderEmailCiphertext(),
                                tenantId,
                                gmailMessageId,
                                EncryptedField.SENDER_EMAIL))
                .isEqualTo("alice@example.com");
        assertThat(
                        cipher.decrypt(
                                projection.getSubjectCiphertext(),
                                tenantId,
                                gmailMessageId,
                                EncryptedField.SUBJECT))
                .isEqualTo("Welcome to Zero Mail");
        assertThat(
                        cipher.decrypt(
                                projection.getSnippetCiphertext(),
                                tenantId,
                                gmailMessageId,
                                EncryptedField.SNIPPET))
                .isEqualTo("Hi, here is a snippet preview...");

        // Sender hash matches what the cipher would compute independently.
        assertThat(projection.getSenderEmailHash())
                .isEqualTo(cipher.hashSenderEmail("alice@example.com"));
    }

    @Test
    void upsert_drops_INBOX_label_flips_state_to_OUT_OF_INBOX_on_re_observe() {
        UUID tenantId = seedTenant();
        String gmailMessageId = "190000000000bb02";
        Instant receivedAt = Instant.now();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            inboxProjectionWriteService.upsert(
                                    new InboxProjectionUpsertCommand(
                                            tenantId,
                                            gmailMessageId,
                                            "thread-flip",
                                            "bob@example.com",
                                            null,
                                            null,
                                            null,
                                            false,
                                            receivedAt,
                                            List.of("INBOX", "UNREAD"),
                                            100L));
                            inboxProjectionWriteService.upsert(
                                    new InboxProjectionUpsertCommand(
                                            tenantId,
                                            gmailMessageId,
                                            "thread-flip",
                                            "bob@example.com",
                                            null,
                                            null,
                                            null,
                                            false,
                                            receivedAt,
                                            List.of("Label_42"), // INBOX gone, UNREAD gone
                                            200L));
                        });

        GmailInboxProjectionEntity projection =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        projectionRepository
                                                .findById(
                                                        new GmailInboxProjectionId(
                                                                tenantId, gmailMessageId))
                                                .orElseThrow());

        assertThat(projection.getInboxState()).isEqualTo(InboxState.OUT_OF_INBOX);
        assertThat(projection.isUnread()).isFalse();
        assertThat(projection.getSourceHistoryId()).isEqualTo(200L);
        assertThat(projection.getVersion()).isEqualTo(1);
    }

    @Test
    void markRead_drops_unread_label_and_flips_flag_and_bumps_version() {
        UUID tenantId = seedTenant();
        String gmailMessageId = "190000000000bb03";
        Instant receivedAt = Instant.now();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            inboxProjectionWriteService.upsert(
                                    new InboxProjectionUpsertCommand(
                                            tenantId,
                                            gmailMessageId,
                                            "thread-mark-read",
                                            "carol@example.com",
                                            "Carol Reader",
                                            "Read me later",
                                            "Snippet about the thing",
                                            false,
                                            receivedAt,
                                            List.of("INBOX", "UNREAD", "Label_99"),
                                            300L));
                            inboxProjectionWriteService.markRead(tenantId, gmailMessageId);
                        });

        GmailInboxProjectionEntity projection =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        projectionRepository
                                                .findById(
                                                        new GmailInboxProjectionId(
                                                                tenantId, gmailMessageId))
                                                .orElseThrow());

        assertThat(projection.isUnread()).isFalse();
        assertThat(projection.getLabelIds())
                .as("UNREAD must be dropped while other labels are preserved")
                .containsExactlyInAnyOrder("INBOX", "Label_99");
        assertThat(projection.getVersion())
                .as("version bumps from initial upsert (0) → markRead (1)")
                .isEqualTo(1);
        // Ciphertext stays untouched — AAD invariants are unchanged, so the projection still
        // decrypts the same plaintext for subject / snippet / sender fields.
        assertThat(
                        cipher.decrypt(
                                projection.getSubjectCiphertext(),
                                tenantId,
                                gmailMessageId,
                                EncryptedField.SUBJECT))
                .isEqualTo("Read me later");
    }

    @Test
    void markRead_is_noop_when_projection_row_does_not_exist() {
        UUID tenantId = seedTenant();
        String missingGmailMessageId = "190000000000bbff";

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> inboxProjectionWriteService.markRead(tenantId, missingGmailMessageId));

        boolean rowPresent =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        projectionRepository
                                                .findById(
                                                        new GmailInboxProjectionId(
                                                                tenantId, missingGmailMessageId))
                                                .isPresent());
        assertThat(rowPresent)
                .as("missing projection row must remain absent — markRead does NOT insert")
                .isFalse();
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
