package com.zeromail.core.inbox.usecases;

import com.zeromail.core.inbox.domain.EncryptedField;
import com.zeromail.core.inbox.domain.InboxProjectionDataSource;
import com.zeromail.core.inbox.domain.MessageClass;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionEntity;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side query for the inbox list backed by {@code gmail_inbox_projection} (Phase B Wave 0).
 *
 * <p>Wave 0 deliverable: standalone DB read + decrypt path. The orchestrator that decides between
 * this service and the live-Gmail fallback lands in Wave 1; {@code
 * RecentInboxReadService.fetchPage} stays untouched here so the existing API + tests remain green
 * during the refactor.
 *
 * <p>Decrypt happens at this use-case boundary (single cipher entry point). Per-row {@code
 * expires_at > NOW()} filter is enforced in the native query so stale rows do not leak into the
 * response — the Wave 1 orchestrator can fall back to live Gmail for the gap.
 */
@Service
public class InboxProjectionReadService {

    /**
     * Page limits mirror {@code RecentInboxReadService} so the orchestrator (Wave 1) can hand the
     * same limit to either path without re-clamping. Wave 0 enforces the bounds locally so the
     * service is usable in isolation.
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 20;

    private final GmailInboxProjectionRepository projectionRepository;
    private final InboxProjectionCipher cipher;
    private final InboxProjectionCursorCodec cursorCodec;

    public InboxProjectionReadService(
            GmailInboxProjectionRepository projectionRepository,
            InboxProjectionCipher cipher,
            InboxProjectionCursorCodec cursorCodec) {
        this.projectionRepository =
                Objects.requireNonNull(
                        projectionRepository, "projectionRepository must not be null");
        this.cipher = Objects.requireNonNull(cipher, "cipher must not be null");
        this.cursorCodec = Objects.requireNonNull(cursorCodec, "cursorCodec must not be null");
    }

    /**
     * Fetch a page of inbox rows for the given tenant. {@code cursor = null|blank} returns the
     * newest rows (first page). Passing a previous page's {@code nextCursor} returns the next
     * keyset window. Rows past {@code expires_at} are filtered out at the SQL layer.
     *
     * @throws InvalidProjectionCursorException when the cursor fails decode or signature checks.
     */
    @Transactional(readOnly = true)
    public InboxProjectionPage fetchInboxPage(
            UUID tenantId, UUID gmailConnectionId, String cursor, int requestedLimit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailConnectionId, "gmailConnectionId must not be null");
        InboxProjectionCursor decodedCursor = cursorCodec.decode(cursor);
        int pageLimit = effectiveLimit(requestedLimit);

        List<GmailInboxProjectionEntity> rows =
                projectionRepository.findInboxPage(
                        tenantId,
                        gmailConnectionId,
                        decodedCursor.receivedAt(),
                        decodedCursor.gmailMessageId(),
                        pageLimit);

        ArrayList<InboxProjectionMessage> items = new ArrayList<>(rows.size());
        for (GmailInboxProjectionEntity row : rows) {
            items.add(toInboxProjectionMessage(row, tenantId));
        }

        String nextCursor = nextCursorFor(rows, pageLimit);
        return new InboxProjectionPage(
                List.copyOf(items), nextCursor, InboxProjectionDataSource.PROJECTION);
    }

    /**
     * Phase 12 W5 (CAL-TRIAGE-03): expose the calendar {@code message_class} column to the triage
     * rule-evaluation factory so the {@code PresetCalendarMatcher} can fire deterministically.
     * Returns {@link Optional#empty()} when no projection row exists yet (Pub/Sub ahead of UPSERT)
     * or when the W4 classifier has not (yet) written a classification for this message.
     *
     * <p>This is a single-row, single-column lookup; no decrypt, no PII surfaces. The native query
     * already constrains by {@code (tenant_id, gmail_connection_id, gmail_message_id)} so the
     * tenant isolation invariant holds without an extra check here.
     */
    @Transactional(readOnly = true)
    public Optional<MessageClass> findMessageClass(
            UUID tenantId, UUID gmailConnectionId, String gmailMessageId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailConnectionId, "gmailConnectionId must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        return projectionRepository
                .findByTenantConnectionAndMessage(tenantId, gmailConnectionId, gmailMessageId)
                .flatMap(GmailInboxProjectionEntity::getMessageClassOptional);
    }

    private static int effectiveLimit(int requestedLimit) {
        int positiveLimit = requestedLimit < 1 ? DEFAULT_PAGE_SIZE : requestedLimit;
        return Math.min(positiveLimit, MAX_PAGE_SIZE);
    }

    private String nextCursorFor(List<GmailInboxProjectionEntity> rows, int pageLimit) {
        if (rows.size() < pageLimit) {
            return null;
        }
        GmailInboxProjectionEntity lastRow = rows.get(rows.size() - 1);
        return cursorCodec.encode(lastRow.getReceivedAt(), lastRow.getGmailMessageId());
    }

    private InboxProjectionMessage toInboxProjectionMessage(
            GmailInboxProjectionEntity row, UUID tenantId) {
        String gmailMessageId = row.getGmailMessageId();
        String decodedSenderEmail =
                cipher.decrypt(
                        row.getSenderEmailCiphertext(),
                        tenantId,
                        gmailMessageId,
                        EncryptedField.SENDER_EMAIL);
        String decodedSenderDisplayName =
                cipher.decrypt(
                        row.getSenderDisplayNameCiphertext(),
                        tenantId,
                        gmailMessageId,
                        EncryptedField.SENDER_DISPLAY_NAME);
        String decodedSubject =
                cipher.decrypt(
                        row.getSubjectCiphertext(),
                        tenantId,
                        gmailMessageId,
                        EncryptedField.SUBJECT);
        String decodedSnippet =
                cipher.decrypt(
                        row.getSnippetCiphertext(),
                        tenantId,
                        gmailMessageId,
                        EncryptedField.SNIPPET);

        List<String> rowLabelIds =
                row.getLabelIds() == null ? List.of() : List.of(row.getLabelIds());

        return new InboxProjectionMessage(
                gmailMessageId,
                row.getGmailThreadId(),
                decodedSubject,
                decodedSnippet,
                synthesizeFromHeader(decodedSenderEmail, decodedSenderDisplayName),
                List.of(),
                List.of(),
                row.getReceivedAt(),
                rowLabelIds,
                List.of(),
                row.isUnread(),
                row.isHasAttachment(),
                row.getMessageClassOptional(),
                row.getEventDtOptional());
    }

    /**
     * Build the {@code From} display string the controller hands to the frontend. Format mirrors
     * what Gmail returns in the raw header: {@code "Display Name" <email>} when a display name is
     * present, otherwise the bare email. Display name is wrapped in double quotes only when it
     * contains a character that RFC 5322 says must be quoted (comma, angle brackets, double quote);
     * otherwise it is emitted unquoted to match Gmail's wire format.
     */
    private static String synthesizeFromHeader(String email, String displayName) {
        if (email == null || email.isBlank()) {
            return displayName == null ? "" : displayName.trim();
        }
        String trimmedEmail = email.trim();
        if (displayName == null || displayName.isBlank()) {
            return trimmedEmail;
        }
        String trimmedDisplayName = displayName.trim();
        String renderedDisplayName =
                needsQuoting(trimmedDisplayName) ? quote(trimmedDisplayName) : trimmedDisplayName;
        return renderedDisplayName + " <" + trimmedEmail + ">";
    }

    private static boolean needsQuoting(String displayName) {
        for (int characterIndex = 0; characterIndex < displayName.length(); characterIndex++) {
            char currentChar = displayName.charAt(characterIndex);
            if (currentChar == ','
                    || currentChar == '<'
                    || currentChar == '>'
                    || currentChar == '"'
                    || currentChar == ';') {
                return true;
            }
        }
        return false;
    }

    private static String quote(String displayName) {
        return "\"" + displayName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
