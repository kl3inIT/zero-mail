package com.zeromail.core.inbox.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for the Gmail inbox display projection.
 *
 * <p>The single mutator is {@link #upsertProjection}; ArchUnit (Wave 4) enforces that it is invoked
 * only from {@code InboxProjectionWriteService}. JPA {@code save*} APIs are not used by the
 * production code path — they exist only for tests that bypass the cipher to seed direct rows.
 */
public interface GmailInboxProjectionRepository
        extends JpaRepository<GmailInboxProjectionEntity, GmailInboxProjectionId> {

    /**
     * Native UPSERT for one projection row. {@code refreshed_at} and {@code version} are bumped on
     * every conflicting write so the row reflects the latest observed Gmail state. {@code
     * expires_at} is supplied by the caller (= refreshed_at + 90 days, refresh-based TTL).
     *
     * <p>Cast on the labelIds parameter is required because Hibernate cannot infer the Postgres
     * {@code text[]} target type from the Java {@code String[]} bind without help.
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO gmail_inbox_projection (
                        tenant_id, gmail_message_id, gmail_thread_id,
                        sender_email_hash, sender_email_ciphertext,
                        sender_display_name_ciphertext, subject_ciphertext, snippet_ciphertext,
                        has_attachment, received_at, label_ids,
                        inbox_state, unread, source_history_id,
                        refreshed_at, expires_at, version)
                    VALUES (
                        :tenantId, :gmailMessageId, :gmailThreadId,
                        :senderEmailHash, :senderEmailCiphertext,
                        :senderDisplayNameCiphertext, :subjectCiphertext, :snippetCiphertext,
                        :hasAttachment, :receivedAt, CAST(:labelIds AS text[]),
                        :inboxState, :unread, :sourceHistoryId,
                        :refreshedAt, :expiresAt, 0)
                    ON CONFLICT (tenant_id, gmail_message_id) DO UPDATE SET
                        gmail_thread_id = EXCLUDED.gmail_thread_id,
                        sender_email_hash = EXCLUDED.sender_email_hash,
                        sender_email_ciphertext = EXCLUDED.sender_email_ciphertext,
                        sender_display_name_ciphertext = EXCLUDED.sender_display_name_ciphertext,
                        subject_ciphertext = EXCLUDED.subject_ciphertext,
                        snippet_ciphertext = EXCLUDED.snippet_ciphertext,
                        has_attachment = EXCLUDED.has_attachment,
                        received_at = EXCLUDED.received_at,
                        label_ids = EXCLUDED.label_ids,
                        inbox_state = EXCLUDED.inbox_state,
                        unread = EXCLUDED.unread,
                        source_history_id = EXCLUDED.source_history_id,
                        refreshed_at = EXCLUDED.refreshed_at,
                        expires_at = EXCLUDED.expires_at,
                        version = gmail_inbox_projection.version + 1
                    """,
            nativeQuery = true)
    @Transactional
    int upsertProjection(
            @Param("tenantId") UUID tenantId,
            @Param("gmailMessageId") String gmailMessageId,
            @Param("gmailThreadId") String gmailThreadId,
            @Param("senderEmailHash") byte[] senderEmailHash,
            @Param("senderEmailCiphertext") byte[] senderEmailCiphertext,
            @Param("senderDisplayNameCiphertext") byte[] senderDisplayNameCiphertext,
            @Param("subjectCiphertext") byte[] subjectCiphertext,
            @Param("snippetCiphertext") byte[] snippetCiphertext,
            @Param("hasAttachment") boolean hasAttachment,
            @Param("receivedAt") Instant receivedAt,
            @Param("labelIds") String[] labelIds,
            @Param("inboxState") String inboxState,
            @Param("unread") boolean unread,
            @Param("sourceHistoryId") long sourceHistoryId,
            @Param("refreshedAt") Instant refreshedAt,
            @Param("expiresAt") Instant expiresAt);
}
