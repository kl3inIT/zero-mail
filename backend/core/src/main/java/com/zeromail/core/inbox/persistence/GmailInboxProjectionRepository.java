package com.zeromail.core.inbox.persistence;

import java.time.Instant;
import java.util.List;
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
                        tenant_id, gmail_connection_id, gmail_message_id, gmail_thread_id,
                        sender_email_hash, sender_email_ciphertext,
                        sender_display_name_ciphertext, subject_ciphertext, snippet_ciphertext,
                        has_attachment, received_at, label_ids,
                        inbox_state, unread, source_history_id,
                        refreshed_at, expires_at, version)
                    VALUES (
                        :tenantId, :gmailConnectionId, :gmailMessageId, :gmailThreadId,
                        :senderEmailHash, :senderEmailCiphertext,
                        :senderDisplayNameCiphertext, :subjectCiphertext, :snippetCiphertext,
                        :hasAttachment, :receivedAt, CAST(:labelIds AS text[]),
                        :inboxState, :unread, :sourceHistoryId,
                        :refreshedAt, :expiresAt, 0)
                    ON CONFLICT (tenant_id, gmail_connection_id, gmail_message_id) DO UPDATE SET
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
            @Param("gmailConnectionId") UUID gmailConnectionId,
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

    /**
     * Read-side page query for the inbox list (Phase B Wave 0).
     *
     * <p>Hits the partial index {@code idx_gmail_inbox_projection_list (tenant_id, received_at
     * DESC, gmail_message_id DESC) WHERE inbox_state = 'INBOX'} for a single range scan. Per-row
     * {@code expires_at > NOW()} predicate is filtered after the index seek so stale rows drop out
     * of the page and the orchestrator (Wave 1) can fall back to live Gmail for the gap.
     *
     * <p>Pagination is keyset: pass {@code (beforeReceivedAt, beforeMessageId)} extracted from the
     * previous page's last row. The first page passes {@code null} for both — the {@code IS NULL}
     * guard short-circuits the keyset predicate and returns the newest rows.
     *
     * <p>Native query because Hibernate's {@code @TenantId} resolver only applies to JPQL — we set
     * {@code tenant_id = :tenantId} explicitly. Casts on the timestamp parameter let Postgres infer
     * the bind type when the value is null.
     */
    @Query(
            value =
                    """
                    SELECT * FROM gmail_inbox_projection
                    WHERE tenant_id = :tenantId
                      AND gmail_connection_id = :gmailConnectionId
                      AND inbox_state = 'INBOX'
                      AND expires_at > NOW()
                      AND (
                          CAST(:beforeReceivedAt AS timestamptz) IS NULL
                          OR received_at < CAST(:beforeReceivedAt AS timestamptz)
                          OR (
                              received_at = CAST(:beforeReceivedAt AS timestamptz)
                              AND gmail_message_id < :beforeMessageId
                          )
                      )
                    ORDER BY received_at DESC, gmail_message_id DESC
                    LIMIT :pageLimit
                    """,
            nativeQuery = true)
    List<GmailInboxProjectionEntity> findInboxPage(
            @Param("tenantId") UUID tenantId,
            @Param("gmailConnectionId") UUID gmailConnectionId,
            @Param("beforeReceivedAt") Instant beforeReceivedAt,
            @Param("beforeMessageId") String beforeMessageId,
            @Param("pageLimit") int pageLimit);

    /**
     * Mark a single projection row as read (Phase B Wave 2). Drops the {@code UNREAD} label from
     * {@code label_ids} via {@code array_remove}, flips the denormalized {@code unread} flag to
     * false, bumps {@code refreshed_at} and {@code version}.
     *
     * <p>Returns the affected row count: {@code 0} when the projection has no row for the message
     * yet (e.g. backfill has not reached it). The caller treats {@code 0} as a no-op — the next
     * Pub/Sub event will UPSERT the row with the up-to-date Gmail state anyway.
     *
     * <p>Ciphertext columns are untouched; the existing AAD bound to the original plaintext is
     * still valid because the {@code (tenantId, gmailMessageId, fieldName)} triple has not changed.
     */
    @Modifying
    @Query(
            value =
                    """
                    UPDATE gmail_inbox_projection
                    SET unread = false,
                        label_ids = array_remove(label_ids, 'UNREAD'),
                        refreshed_at = NOW(),
                        version = version + 1
                    WHERE tenant_id = :tenantId
                      AND gmail_message_id = :gmailMessageId
                    """,
            nativeQuery = true)
    @Transactional
    int markRead(@Param("tenantId") UUID tenantId, @Param("gmailMessageId") String gmailMessageId);
}
