package com.zeromail.core.inbox.usecases;

import com.zeromail.core.inbox.domain.EncryptedField;
import com.zeromail.core.inbox.domain.InboxState;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single write entry point for the Gmail inbox projection.
 *
 * <p>ArchUnit (Wave 4) enforces that {@link GmailInboxProjectionRepository#upsertProjection} is
 * called only from here. Callers (Pub/Sub delivery listener in Wave 2; backfill service in Wave 3;
 * read-swap path in Phase B) pass already-extracted metadata; this service handles the encryption,
 * sender hashing, expires_at derivation, and the row-level UPSERT.
 */
@Service
public class InboxProjectionWriteService {

    /**
     * Refresh-based TTL. Re-observing a message bumps {@code expires_at} forward by 90 days so
     * actively-touched rows do not expire. See the Phase A plan for the rationale (received_at +
     * 90 would expire backfilled-old-mail accounts immediately).
     */
    private static final Duration PROJECTION_TTL = Duration.ofDays(90);

    private static final String UNREAD_LABEL = "UNREAD";
    private static final String INBOX_LABEL = "INBOX";

    private final GmailInboxProjectionRepository projectionRepository;
    private final InboxProjectionCipher cipher;
    private final java.time.Clock clock;

    public InboxProjectionWriteService(
            GmailInboxProjectionRepository projectionRepository,
            InboxProjectionCipher cipher,
            java.time.Clock clock) {
        this.projectionRepository =
                Objects.requireNonNull(projectionRepository, "projectionRepository must not be null");
        this.cipher = Objects.requireNonNull(cipher, "cipher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * UPSERT one inbox projection row from already-extracted Gmail message metadata.
     *
     * <p>The {@code subject}, {@code snippet}, and {@code senderDisplayName} arguments may be null
     * — the Pub/Sub observed listener's metadata fetch deliberately keeps the projection minimal
     * (sender email + thread id + labels + received_at) so the same DB row can be backfilled with
     * the richer payload later without breaking observed flow. Backfill (Wave 3) supplies the full
     * set.
     *
     * <p>The {@code inbox_state} value is derived from the Gmail label set so callers don't have
     * to make the decision: INBOX label present → INBOX; otherwise → OUT_OF_INBOX. TOMBSTONED is
     * reserved for explicit Gmail 404 / messageDeleted detection in a later phase.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(InboxProjectionUpsertCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID tenantId = command.tenantId();
        String gmailMessageId = command.gmailMessageId();
        Instant refreshedAt = Instant.now(clock);
        Instant expiresAt = refreshedAt.plus(PROJECTION_TTL);

        byte[] senderEmailCiphertext =
                cipher.encrypt(
                        command.senderEmail(), tenantId, gmailMessageId, EncryptedField.SENDER_EMAIL);
        byte[] senderDisplayNameCiphertext =
                cipher.encrypt(
                        command.senderDisplayName(),
                        tenantId,
                        gmailMessageId,
                        EncryptedField.SENDER_DISPLAY_NAME);
        byte[] subjectCiphertext =
                cipher.encrypt(
                        command.subject(), tenantId, gmailMessageId, EncryptedField.SUBJECT);
        byte[] snippetCiphertext =
                cipher.encrypt(
                        command.snippet(), tenantId, gmailMessageId, EncryptedField.SNIPPET);
        byte[] senderEmailHash = cipher.hashSenderEmail(command.senderEmail());

        String[] labelIds = command.labelIds().toArray(new String[0]);
        Arrays.sort(labelIds); // canonical order so repeated UPSERT with the same set is stable
        InboxState inboxState = deriveInboxState(command.labelIds());
        boolean unread = command.labelIds().contains(UNREAD_LABEL);

        projectionRepository.upsertProjection(
                tenantId,
                gmailMessageId,
                command.gmailThreadId(),
                senderEmailHash,
                senderEmailCiphertext,
                senderDisplayNameCiphertext,
                subjectCiphertext,
                snippetCiphertext,
                command.hasAttachment(),
                command.receivedAt(),
                labelIds,
                inboxState.id(),
                unread,
                command.sourceHistoryId(),
                refreshedAt,
                expiresAt);
    }

    /**
     * Local mark-read write hook (Phase B Wave 2). Called by {@code TriageGmailWriter.markRead}
     * after the corresponding Gmail {@code users.messages.modify} succeeds, so the projection row
     * reflects the new state immediately and the next inbox-list fetch from the DB returns the
     * same value the optimistic UI already shows.
     *
     * <p>Ciphertext columns and AAD are untouched — only {@code unread} and the {@code label_ids}
     * UNREAD entry change. Returns silently when the projection has no row for the message yet;
     * the next Pub/Sub UPSERT will reconcile.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRead(UUID tenantId, String gmailMessageId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        if (gmailMessageId.isBlank()) {
            throw new IllegalArgumentException("gmailMessageId must not be blank");
        }
        projectionRepository.markRead(tenantId, gmailMessageId);
    }

    private static InboxState deriveInboxState(List<String> labelIds) {
        return labelIds.contains(INBOX_LABEL) ? InboxState.INBOX : InboxState.OUT_OF_INBOX;
    }
}
