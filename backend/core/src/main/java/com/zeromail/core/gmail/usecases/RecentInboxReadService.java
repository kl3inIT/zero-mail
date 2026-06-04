package com.zeromail.core.gmail.usecases;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GmailMessageHeaders;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.inbox.domain.InboxProjectionDataSource;
import com.zeromail.core.inbox.usecases.InboxProjectionMessage;
import com.zeromail.core.inbox.usecases.InboxProjectionPage;
import com.zeromail.core.inbox.usecases.InboxProjectionReadService;
import com.zeromail.core.inbox.usecases.InvalidProjectionCursorException;
import com.zeromail.core.shared.crypto.CryptoProperties;
import com.zeromail.core.shared.html.SafeHtmlSanitizer;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecentInboxReadService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 20;
    public static final int MAX_MESSAGES = 100;

    /**
     * Source tag prefix for Wave 1 orchestrator cursors. Pagination must stay on the source that
     * issued the first page; the prefix lets {@link #fetchPage(UUID, String, int)} dispatch the
     * follow-up cursor without parsing the inner payload twice.
     */
    private static final String PROJECTION_CURSOR_PREFIX = "P";

    private static final String LIVE_GMAIL_CURSOR_PREFIX = "G";

    private static final Logger log = LoggerFactory.getLogger(RecentInboxReadService.class);

    private static final Duration GMAIL_REQUEST_TIMEOUT = Duration.ofSeconds(6);
    private static final int SUBJECT_MAX_LENGTH = 200;
    private static final int SNIPPET_MAX_LENGTH = 240;
    private static final int HEADER_MAX_LENGTH = 500;
    private static final int RENDERED_TEXT_MAX_LENGTH = 12_000;
    private static final int RENDERED_HTML_MAX_LENGTH = 200_000;
    private static final int INLINE_IMAGE_MAX_BYTES = 1_000_000;
    private static final int INLINE_IMAGE_TOTAL_MAX_BYTES = 3_000_000;
    private static final List<String> METADATA_HEADERS = List.of("From", "To", "Cc", "Subject");
    private static final String MESSAGE_LIST_FIELDS = "messages(id,threadId),nextPageToken";
    private static final String METADATA_FIELDS =
            "id,threadId,labelIds,internalDate,snippet,payload/headers,payload/parts/filename,"
                    + "payload/parts/parts/filename";
    private static final String FULL_FIELDS = "id,threadId,labelIds,internalDate,snippet,payload";

    private final GmailConnectionRepository gmailConnectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final SafeHtmlSanitizer safeHtmlSanitizer;
    private final InboxCursorCodec inboxCursorCodec;
    private final com.zeromail.core.inbox.usecases.InboxBackfillEnqueuer inboxBackfillEnqueuer;
    private final com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository
            inboxSyncStateRepository;
    private final InboxProjectionReadService inboxProjectionReadService;

    public RecentInboxReadService(
            GmailConnectionRepository gmailConnectionRepository,
            GmailApiClientFactory gmailApiClientFactory,
            CryptoProperties cryptoProperties,
            SafeHtmlSanitizer safeHtmlSanitizer,
            com.zeromail.core.inbox.usecases.InboxBackfillEnqueuer inboxBackfillEnqueuer,
            com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository
                            inboxSyncStateRepository,
            InboxProjectionReadService inboxProjectionReadService) {
        this.gmailConnectionRepository =
                Objects.requireNonNull(
                        gmailConnectionRepository, "gmailConnectionRepository must not be null");
        this.gmailApiClientFactory =
                Objects.requireNonNull(
                        gmailApiClientFactory, "gmailApiClientFactory must not be null");
        this.safeHtmlSanitizer =
                Objects.requireNonNull(safeHtmlSanitizer, "safeHtmlSanitizer must not be null");
        this.inboxCursorCodec =
                new InboxCursorCodec(
                        Objects.requireNonNull(
                                        cryptoProperties, "cryptoProperties must not be null")
                                .refreshTokenKeyBase64());
        this.inboxBackfillEnqueuer =
                Objects.requireNonNull(
                        inboxBackfillEnqueuer, "inboxBackfillEnqueuer must not be null");
        this.inboxSyncStateRepository =
                Objects.requireNonNull(
                        inboxSyncStateRepository, "inboxSyncStateRepository must not be null");
        this.inboxProjectionReadService =
                Objects.requireNonNull(
                        inboxProjectionReadService,
                        "inboxProjectionReadService must not be null");
    }

    /**
     * Wave 1 read orchestrator. Routes the request between three sources:
     *
     * <ol>
     *   <li><b>First page (cursor null/blank)</b>: if the tenant has never finished a full sync
     *       returns a {@code SYNCING} empty page so the frontend renders the loading banner. Else
     *       queries the projection; a full page wins. A short or empty projection page falls back
     *       to live Gmail (no mixed-source pages).
     *   <li><b>Projection follow-up (cursor prefix {@code P})</b>: strips the prefix, hands the
     *       inner keyset cursor to {@link InboxProjectionReadService}.
     *   <li><b>Live Gmail follow-up (cursor prefix {@code G})</b>: strips the prefix, replays the
     *       legacy pageToken-based path so an in-flight Gmail pagination stays consistent.
     * </ol>
     *
     * <p>Cursor source is sticky: pagination cannot switch sources mid-flight. This avoids
     * expires_at correctness issues and duplicate / skipped rows when stitching keysets against
     * pageTokens.
     */
    @Transactional(readOnly = true)
    public RecentInboxPage fetchPage(UUID tenantId, String cursor, int requestedLimit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        // Lazy backfill trigger (Phase A wave 3): the first time a tenant fetches the inbox after
        // connecting, kick off an asynchronous backfill so the projection is ready by the time
        // Phase B swaps the read path to the DB. Live Gmail still serves the fallback response;
        // enqueue is idempotent via processing_job dedup so concurrent fetches do not stack jobs.
        enqueueBackfillIfFirstFetch(tenantId);

        if (cursor != null && !cursor.isBlank()) {
            String trimmedCursor = cursor.trim();
            String prefix = trimmedCursor.substring(0, 1);
            String innerCursor = trimmedCursor.substring(1);
            return switch (prefix) {
                case PROJECTION_CURSOR_PREFIX ->
                        fetchPageFromProjection(tenantId, innerCursor, requestedLimit);
                case LIVE_GMAIL_CURSOR_PREFIX ->
                        fetchPageFromLiveGmail(tenantId, innerCursor, requestedLimit);
                default ->
                        throw new RecentInboxUnavailableException(
                                RecentInboxUnavailableReason.INVALID_CURSOR);
            };
        }

        if (needsFullSyncFirst(tenantId)) {
            return RecentInboxPage.syncing(MAX_MESSAGES);
        }

        int firstPageLimit = effectiveLimit(requestedLimit, 0);
        RecentInboxPage projectionPage = fetchPageFromProjection(tenantId, null, requestedLimit);
        if (projectionPage.messages().size() == firstPageLimit && firstPageLimit > 0) {
            return projectionPage;
        }
        log.info(
                "event=inbox_read_fallback tenantId={} reason={} projectionRows={}",
                tenantId,
                projectionPage.messages().isEmpty() ? "projection_empty" : "projection_partial",
                projectionPage.messages().size());
        return fetchPageFromLiveGmail(tenantId, null, requestedLimit);
    }

    private RecentInboxPage fetchPageFromProjection(
            UUID tenantId, String innerCursor, int requestedLimit) {
        InboxProjectionPage projectionPage;
        try {
            projectionPage =
                    inboxProjectionReadService.fetchInboxPage(
                            tenantId, innerCursor, requestedLimit);
        } catch (InvalidProjectionCursorException invalidProjectionCursorException) {
            throw new RecentInboxUnavailableException(
                    RecentInboxUnavailableReason.INVALID_CURSOR,
                    invalidProjectionCursorException);
        }
        List<RecentInboxMessage> messages = toRecentInboxMessages(projectionPage.items());
        String nextCursor =
                projectionPage.nextCursor() == null
                        ? null
                        : PROJECTION_CURSOR_PREFIX + projectionPage.nextCursor();
        return new RecentInboxPage(
                messages, nextCursor, messages.size(), MAX_MESSAGES, projectionPage.dataSource());
    }

    private RecentInboxPage fetchPageFromLiveGmail(
            UUID tenantId, String innerCursor, int requestedLimit) {
        InboxCursor inboxCursor = inboxCursorCodec.decode(innerCursor);
        if (inboxCursor.loadedCount() >= MAX_MESSAGES) {
            return new RecentInboxPage(
                    List.of(),
                    null,
                    inboxCursor.loadedCount(),
                    MAX_MESSAGES,
                    InboxProjectionDataSource.LIVE_GMAIL);
        }
        int gmailPageLimit = effectiveLimit(requestedLimit, inboxCursor.loadedCount());
        try {
            Gmail gmail = gmailForTenant(tenantId);
            ListMessagesResponse listResponse =
                    gmail.users()
                            .messages()
                            .list("me")
                            .setLabelIds(List.of("INBOX"))
                            .setMaxResults((long) gmailPageLimit)
                            .setPageToken(inboxCursor.pageToken())
                            .setFields(MESSAGE_LIST_FIELDS)
                            .execute();
            List<Message> messageReferences =
                    listResponse.getMessages() == null ? List.of() : listResponse.getMessages();
            Map<String, String> labelNamesById = fetchLabelNamesById(gmail);
            List<RecentInboxMessage> messages =
                    fetchMessageMetadata(gmail, messageReferences, labelNamesById);
            int loadedCount = inboxCursor.loadedCount() + messages.size();
            String nextCursor =
                    loadedCount >= MAX_MESSAGES || listResponse.getNextPageToken() == null
                            ? null
                            : LIVE_GMAIL_CURSOR_PREFIX
                                    + inboxCursorCodec.encode(
                                            listResponse.getNextPageToken(), loadedCount);
            return new RecentInboxPage(
                    messages,
                    nextCursor,
                    loadedCount,
                    MAX_MESSAGES,
                    InboxProjectionDataSource.LIVE_GMAIL);
        } catch (InvalidGrantException invalidGrantException) {
            throw new RecentInboxUnavailableException(
                    RecentInboxUnavailableReason.REVOKED, invalidGrantException);
        } catch (GoogleJsonResponseException googleResponseException) {
            throw new RecentInboxUnavailableException(
                    mapGoogleResponse(googleResponseException), googleResponseException);
        } catch (IOException ioException) {
            throw new RecentInboxUnavailableException(
                    RecentInboxUnavailableReason.GMAIL_UNAVAILABLE, ioException);
        }
    }

    private static List<RecentInboxMessage> toRecentInboxMessages(
            List<InboxProjectionMessage> projectionItems) {
        ArrayList<RecentInboxMessage> recentInboxMessages =
                new ArrayList<>(projectionItems.size());
        for (InboxProjectionMessage projectionItem : projectionItems) {
            recentInboxMessages.add(
                    new RecentInboxMessage(
                            projectionItem.gmailMessageId(),
                            projectionItem.gmailThreadId(),
                            projectionItem.subject(),
                            projectionItem.snippet(),
                            projectionItem.from(),
                            projectionItem.to(),
                            projectionItem.cc(),
                            projectionItem.receivedAt(),
                            projectionItem.labelIds(),
                            toRecentInboxLabels(projectionItem.labels()),
                            projectionItem.unread(),
                            projectionItem.hasAttachment()));
        }
        return List.copyOf(recentInboxMessages);
    }

    private static List<RecentInboxLabel> toRecentInboxLabels(
            List<com.zeromail.core.inbox.usecases.InboxProjectionLabel> projectionLabels) {
        ArrayList<RecentInboxLabel> recentInboxLabels = new ArrayList<>(projectionLabels.size());
        for (com.zeromail.core.inbox.usecases.InboxProjectionLabel projectionLabel
                : projectionLabels) {
            recentInboxLabels.add(
                    new RecentInboxLabel(projectionLabel.id(), projectionLabel.name()));
        }
        return List.copyOf(recentInboxLabels);
    }

    /**
     * Freshness gate for the first-page orchestrator branch. Returns true when the tenant has not
     * yet completed a full sync — equivalent to "no row in sync_state OR last_full_sync_at IS NULL".
     */
    private boolean needsFullSyncFirst(UUID tenantId) {
        return inboxSyncStateRepository
                .findById(tenantId)
                .map(syncState -> syncState.getLastFullSyncAt() == null)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public RecentInboxMessageDetail fetchMessageDetail(UUID tenantId, String gmailMessageId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        String messageId = requireMessageId(gmailMessageId);
        try {
            Gmail gmail = gmailForTenant(tenantId);
            Message gmailMessage =
                    gmail.users()
                            .messages()
                            .get("me", messageId)
                            .setFormat("full")
                            .setFields(FULL_FIELDS)
                            .execute();
            return renderMessageDetail(gmail, gmailMessage);
        } catch (InvalidGrantException invalidGrantException) {
            throw new RecentInboxUnavailableException(
                    RecentInboxUnavailableReason.REVOKED, invalidGrantException);
        } catch (GoogleJsonResponseException googleResponseException) {
            throw new RecentInboxUnavailableException(
                    mapGoogleResponse(googleResponseException), googleResponseException);
        } catch (IOException ioException) {
            throw new RecentInboxUnavailableException(
                    RecentInboxUnavailableReason.GMAIL_UNAVAILABLE, ioException);
        }
    }

    /**
     * Fetch and render the saved Gmail reply draft for a thread. The draft body is user-authored
     * draft data (the user owns it, reviews it, decides whether to send it), not extracted email
     * content received from Gmail — so it may be shown in-place (privacy carve-out). It is rendered
     * live and never persisted. Reuses the same decode + sanitize pipeline as {@link
     * #fetchMessageDetail}.
     */
    @Transactional(readOnly = true)
    public RecentInboxMessageDetail fetchDraftDetail(UUID tenantId, String gmailDraftId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (gmailDraftId == null || gmailDraftId.isBlank()) {
            throw new IllegalArgumentException("gmailDraftId must not be blank");
        }
        try {
            Gmail gmail = gmailForTenant(tenantId);
            Draft draft =
                    gmail.users()
                            .drafts()
                            .get("me", gmailDraftId.trim())
                            .setFormat("full")
                            .execute();
            Message draftMessage = draft.getMessage();
            if (draftMessage == null) {
                throw new RecentInboxUnavailableException(
                        RecentInboxUnavailableReason.MESSAGE_NOT_FOUND);
            }
            return renderMessageDetail(gmail, draftMessage);
        } catch (InvalidGrantException invalidGrantException) {
            throw new RecentInboxUnavailableException(
                    RecentInboxUnavailableReason.REVOKED, invalidGrantException);
        } catch (GoogleJsonResponseException googleResponseException) {
            throw new RecentInboxUnavailableException(
                    mapGoogleResponse(googleResponseException), googleResponseException);
        } catch (IOException ioException) {
            throw new RecentInboxUnavailableException(
                    RecentInboxUnavailableReason.GMAIL_UNAVAILABLE, ioException);
        }
    }

    private RecentInboxMessageDetail renderMessageDetail(Gmail gmail, Message gmailMessage)
            throws IOException {
        String messageId = gmailMessage.getId();
        RecentInboxMessage message = toRecentInboxMessage(gmailMessage, fetchLabelNamesById(gmail));
        MessagePart payload = gmailMessage.getPayload();
        String renderedText =
                cap(decodedMimeBody(payload, "text/plain", true), RENDERED_TEXT_MAX_LENGTH);
        String decodedHtml =
                cap(decodedMimeBody(payload, "text/html", false), RENDERED_HTML_MAX_LENGTH);
        String renderedHtml =
                decodedHtml.isBlank()
                        ? ""
                        : safeHtmlSanitizer.sanitizeEmailHtml(
                                cap(
                                        inlineCidImageSources(
                                                gmail, messageId, payload, decodedHtml),
                                        RENDERED_HTML_MAX_LENGTH));
        return new RecentInboxMessageDetail(message, renderedText, renderedHtml);
    }

    /**
     * Best-effort fetch of a single message's body text for the weekly content digest. Prefers the
     * decoded {@code text/plain} part and falls back to the raw {@code text/html} part (the LLM
     * gateway strips HTML and prompt-injection-hardens before any model call). Returns {@link
     * Optional#empty()} for any failure — message deleted, grant revoked, Gmail unavailable — so
     * one unreadable message never fails the whole digest. The body is returned to the caller in
     * memory only and is never persisted or logged.
     */
    public Optional<String> fetchPlainTextForDigest(UUID tenantId, String gmailMessageId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (gmailMessageId == null || gmailMessageId.isBlank()) {
            return Optional.empty();
        }
        try {
            Gmail gmail = gmailForTenant(tenantId);
            Message gmailMessage =
                    gmail.users()
                            .messages()
                            .get("me", gmailMessageId.trim())
                            .setFormat("full")
                            .setFields(FULL_FIELDS)
                            .execute();
            MessagePart payload = gmailMessage.getPayload();
            String plainText = decodedMimeBody(payload, "text/plain", true);
            String body =
                    plainText.isBlank() ? decodedMimeBody(payload, "text/html", false) : plainText;
            String capped = cap(body, RENDERED_TEXT_MAX_LENGTH);
            return capped.isBlank() ? Optional.empty() : Optional.of(capped);
        } catch (RuntimeException | IOException digestBodyFetchFailure) {
            return Optional.empty();
        }
    }

    private Gmail gmailForTenant(UUID tenantId) throws IOException {
        GmailConnectionEntity gmailConnection =
                gmailConnectionRepository
                        .findByTenantId(tenantId)
                        .orElseThrow(
                                () ->
                                        new RecentInboxUnavailableException(
                                                RecentInboxUnavailableReason.NOT_CONNECTED));
        if (gmailConnection.getStatus() != GmailConnectionStatus.CONNECTED) {
            throw new RecentInboxUnavailableException(RecentInboxUnavailableReason.DISCONNECTED);
        }
        if (gmailConnection.getRefreshTokenEncrypted() == null) {
            throw new RecentInboxUnavailableException(RecentInboxUnavailableReason.NO_READ_GRANT);
        }
        return gmailApiClientFactory.buildClientForConnection(
                gmailConnection, tenantId, GMAIL_REQUEST_TIMEOUT);
    }

    private static int effectiveLimit(int requestedLimit, int loadedCount) {
        int positiveLimit = requestedLimit < 1 ? DEFAULT_PAGE_SIZE : requestedLimit;
        int pageLimit = Math.min(positiveLimit, MAX_PAGE_SIZE);
        int remaining = Math.max(0, MAX_MESSAGES - loadedCount);
        return Math.min(pageLimit, remaining);
    }

    private static String requireMessageId(String gmailMessageId) {
        if (gmailMessageId == null || gmailMessageId.isBlank()) {
            throw new IllegalArgumentException("gmailMessageId must not be blank");
        }
        return gmailMessageId.trim();
    }

    private static List<RecentInboxMessage> fetchMessageMetadata(
            Gmail gmail, List<Message> messageReferences, Map<String, String> labelNamesById)
            throws IOException {
        ArrayList<RecentInboxMessage> messages = new ArrayList<>();
        for (Message messageReference : messageReferences) {
            if (messageReference == null || messageReference.getId() == null) {
                continue;
            }
            Message gmailMessage =
                    gmail.users()
                            .messages()
                            .get("me", messageReference.getId())
                            .setFormat("metadata")
                            .setMetadataHeaders(METADATA_HEADERS)
                            .setFields(METADATA_FIELDS)
                            .execute();
            messages.add(toRecentInboxMessage(gmailMessage, labelNamesById));
        }
        return List.copyOf(messages);
    }

    private static Map<String, String> fetchLabelNamesById(Gmail gmail) throws IOException {
        ListLabelsResponse labelsResponse = gmail.users().labels().list("me").execute();
        List<Label> gmailLabels = labelsResponse.getLabels();
        if (gmailLabels == null || gmailLabels.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> labelNamesById = new LinkedHashMap<>();
        for (Label gmailLabel : gmailLabels) {
            if (gmailLabel == null || gmailLabel.getId() == null || gmailLabel.getName() == null) {
                continue;
            }
            labelNamesById.put(gmailLabel.getId(), gmailLabel.getName());
        }
        return Map.copyOf(labelNamesById);
    }

    private static RecentInboxMessage toRecentInboxMessage(
            Message gmailMessage, Map<String, String> labelNamesById) {
        MessagePart payload = gmailMessage.getPayload();
        List<String> labelIds =
                gmailMessage.getLabelIds() == null
                        ? List.of()
                        : List.copyOf(gmailMessage.getLabelIds());
        return new RecentInboxMessage(
                gmailMessage.getId(),
                gmailMessage.getThreadId(),
                cap(
                        GmailMessageHeaders.firstValue(payload, "Subject").orElse(""),
                        SUBJECT_MAX_LENGTH),
                cap(gmailMessage.getSnippet(), SNIPPET_MAX_LENGTH),
                cap(GmailMessageHeaders.firstValue(payload, "From").orElse(""), HEADER_MAX_LENGTH),
                parseRecipients(GmailMessageHeaders.firstValue(payload, "To").orElse("")),
                parseRecipients(GmailMessageHeaders.firstValue(payload, "Cc").orElse("")),
                internalDate(gmailMessage),
                labelIds,
                labelsFor(labelIds, labelNamesById),
                labelIds.contains("UNREAD"),
                hasAttachment(payload));
    }

    private static List<RecentInboxLabel> labelsFor(
            List<String> labelIds, Map<String, String> labelNamesById) {
        ArrayList<RecentInboxLabel> labels = new ArrayList<>();
        for (String labelId : labelIds) {
            if (labelId == null || labelId.isBlank()) {
                continue;
            }
            labels.add(
                    new RecentInboxLabel(
                            labelId,
                            Objects.requireNonNullElse(labelNamesById.get(labelId), labelId)));
        }
        return List.copyOf(labels);
    }

    private static List<String> parseRecipients(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return List.of();
        }
        ArrayList<String> recipients = new ArrayList<>();
        for (String recipientPart : headerValue.split(",")) {
            String recipient = cap(recipientPart.trim(), HEADER_MAX_LENGTH);
            if (!recipient.isBlank()) {
                recipients.add(recipient);
            }
        }
        return List.copyOf(recipients);
    }

    private static Instant internalDate(Message gmailMessage) {
        Long internalDateMillis = gmailMessage.getInternalDate();
        return internalDateMillis == null
                ? Instant.EPOCH
                : Instant.ofEpochMilli(internalDateMillis);
    }

    private static boolean hasAttachment(MessagePart payload) {
        if (payload == null || payload.getParts() == null) {
            return false;
        }
        for (MessagePart part : payload.getParts()) {
            if (part.getFilename() != null && !part.getFilename().isBlank()) {
                return true;
            }
            if (hasAttachment(part)) {
                return true;
            }
        }
        return false;
    }

    private static String decodedMimeBody(
            MessagePart payload, String targetMimeType, boolean combineMatchingParts) {
        if (payload == null) {
            return "";
        }
        if (isMimePart(payload, targetMimeType) && !hasFilename(payload)) {
            String decodedBody = decodeBody(payload.getBody());
            if (!decodedBody.isBlank()) {
                return decodedBody;
            }
        }
        if (payload.getParts() == null) {
            return "";
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (MessagePart part : payload.getParts()) {
            String partBody = decodedMimeBody(part, targetMimeType, combineMatchingParts);
            if (!partBody.isBlank()) {
                if (!combineMatchingParts) {
                    return partBody;
                }
                if (!bodyBuilder.isEmpty()) {
                    bodyBuilder.append("\n\n");
                }
                bodyBuilder.append(partBody);
            }
        }
        return bodyBuilder.toString();
    }

    private static boolean isMimePart(MessagePart payload, String targetMimeType) {
        String mimeType = payload.getMimeType();
        return mimeType == null
                ? "text/plain".equalsIgnoreCase(targetMimeType)
                : mimeType.equalsIgnoreCase(targetMimeType);
    }

    private static boolean hasFilename(MessagePart payload) {
        return payload.getFilename() != null && !payload.getFilename().isBlank();
    }

    private static String decodeBody(MessagePartBody body) {
        if (body == null || body.getData() == null || body.getData().isBlank()) {
            return "";
        }
        byte[] decodedBytes = decodeBodyBytes(body.getData());
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }

    private static byte[] decodeBodyBytes(String data) {
        if (data == null || data.isBlank()) {
            return new byte[0];
        }
        try {
            String paddedData = data + "=".repeat((4 - data.length() % 4) % 4);
            return Base64.getUrlDecoder().decode(paddedData);
        } catch (IllegalArgumentException invalidBase64) {
            return new byte[0];
        }
    }

    private String inlineCidImageSources(
            Gmail gmail, String messageId, MessagePart payload, String renderedHtml)
            throws IOException {
        Map<String, String> inlineImageDataUris = new LinkedHashMap<>();
        collectInlineImageDataUris(
                gmail, messageId, payload, inlineImageDataUris, new InlineImageBudget());
        if (inlineImageDataUris.isEmpty() || renderedHtml == null || renderedHtml.isBlank()) {
            return renderedHtml;
        }
        return safeHtmlSanitizer.replaceCidImageSources(renderedHtml, inlineImageDataUris);
    }

    private static void collectInlineImageDataUris(
            Gmail gmail,
            String messageId,
            MessagePart payload,
            Map<String, String> inlineImageDataUris,
            InlineImageBudget inlineImageBudget)
            throws IOException {
        if (payload == null) {
            return;
        }
        String contentId = contentId(payload);
        if (!contentId.isBlank() && isInlineImage(payload)) {
            byte[] decodedImageBytes = decodedInlineImageBytes(gmail, messageId, payload);
            if (inlineImageBudget.canInclude(decodedImageBytes.length)) {
                inlineImageBudget.include(decodedImageBytes.length);
                inlineImageDataUris.put(
                        normalizedContentId(contentId),
                        dataUri(payload.getMimeType(), decodedImageBytes));
            }
        }
        if (payload.getParts() == null) {
            return;
        }
        for (MessagePart part : payload.getParts()) {
            collectInlineImageDataUris(
                    gmail, messageId, part, inlineImageDataUris, inlineImageBudget);
        }
    }

    private static boolean isInlineImage(MessagePart payload) {
        String mimeType = payload.getMimeType();
        return mimeType != null && mimeType.toLowerCase().startsWith("image/");
    }

    private static String contentId(MessagePart payload) {
        String rawContentId = GmailMessageHeaders.firstValue(payload, "Content-ID").orElse("");
        if (rawContentId.isBlank()) {
            return "";
        }
        return rawContentId.replace("<", "").replace(">", "").trim();
    }

    private static String normalizedContentId(String rawContentId) {
        if (rawContentId == null || rawContentId.isBlank()) {
            return "";
        }
        String decodedContentId;
        try {
            decodedContentId = URLDecoder.decode(rawContentId, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidEncodedContentId) {
            decodedContentId = rawContentId;
        }
        return decodedContentId.replace("<", "").replace(">", "").trim().toLowerCase(Locale.ROOT);
    }

    private static byte[] decodedInlineImageBytes(
            Gmail gmail, String messageId, MessagePart payload) throws IOException {
        MessagePartBody body = payload.getBody();
        if (body == null) {
            return new byte[0];
        }
        if (body.getData() != null && !body.getData().isBlank()) {
            return decodeBodyBytes(body.getData());
        }
        if (body.getAttachmentId() == null || body.getAttachmentId().isBlank()) {
            return new byte[0];
        }
        MessagePartBody attachmentBody =
                gmail.users()
                        .messages()
                        .attachments()
                        .get("me", messageId, body.getAttachmentId())
                        .execute();
        return decodeBodyBytes(attachmentBody.getData());
    }

    private static String dataUri(String mimeType, byte[] decodedImageBytes) {
        if (decodedImageBytes.length == 0) {
            return "";
        }
        return "data:"
                + mimeType
                + ";base64,"
                + Base64.getEncoder().encodeToString(decodedImageBytes);
    }

    private static final class InlineImageBudget {

        private int totalBytes;

        private boolean canInclude(int decodedImageBytes) {
            return decodedImageBytes > 0
                    && decodedImageBytes <= INLINE_IMAGE_MAX_BYTES
                    && totalBytes + decodedImageBytes <= INLINE_IMAGE_TOTAL_MAX_BYTES;
        }

        private void include(int decodedImageBytes) {
            totalBytes += decodedImageBytes;
        }
    }

    private static String cap(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmedValue = value.trim();
        return trimmedValue.length() <= maxLength
                ? trimmedValue
                : trimmedValue.substring(0, maxLength);
    }

    private static RecentInboxUnavailableReason mapGoogleResponse(
            GoogleJsonResponseException googleResponseException) {
        return switch (googleResponseException.getStatusCode()) {
            case 401, 403 -> RecentInboxUnavailableReason.NO_READ_GRANT;
            case 404 -> RecentInboxUnavailableReason.MESSAGE_NOT_FOUND;
            default -> RecentInboxUnavailableReason.GMAIL_UNAVAILABLE;
        };
    }

    public record RecentInboxPage(
            List<RecentInboxMessage> messages,
            String nextCursor,
            int loadedCount,
            int maxMessages,
            com.zeromail.core.inbox.domain.InboxProjectionDataSource dataSource) {

        public RecentInboxPage {
            messages = List.copyOf(messages);
            Objects.requireNonNull(dataSource, "dataSource must not be null");
        }

        /**
         * Backwards-compatible constructor for the legacy LIVE_GMAIL path. Defaults the data source
         * to {@link com.zeromail.core.inbox.domain.InboxProjectionDataSource#LIVE_GMAIL} so callers
         * that have not yet been migrated to the orchestrator surface keep emitting the prior wire
         * shape semantics.
         */
        public RecentInboxPage(
                List<RecentInboxMessage> messages,
                String nextCursor,
                int loadedCount,
                int maxMessages) {
            this(
                    messages,
                    nextCursor,
                    loadedCount,
                    maxMessages,
                    com.zeromail.core.inbox.domain.InboxProjectionDataSource.LIVE_GMAIL);
        }

        /**
         * Empty page emitted by the Wave 1 orchestrator when the tenant has not finished its first
         * full sync. The frontend renders a "đang đồng bộ" banner instead of the inbox list (Wave 3).
         */
        public static RecentInboxPage syncing(int maxMessages) {
            return new RecentInboxPage(
                    List.of(),
                    null,
                    0,
                    maxMessages,
                    com.zeromail.core.inbox.domain.InboxProjectionDataSource.SYNCING);
        }
    }

    public record RecentInboxMessage(
            String gmailMessageId,
            String gmailThreadId,
            String subject,
            String snippet,
            String from,
            List<String> to,
            List<String> cc,
            Instant receivedAt,
            List<String> labelIds,
            List<RecentInboxLabel> labels,
            boolean unread,
            boolean hasAttachment) {

        public RecentInboxMessage {
            to = List.copyOf(to);
            cc = List.copyOf(cc);
            labelIds = List.copyOf(labelIds);
            labels = List.copyOf(labels);
        }
    }

    public record RecentInboxLabel(String id, String name) {

        public RecentInboxLabel {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(name, "name must not be null");
        }
    }

    public record RecentInboxMessageDetail(
            RecentInboxMessage message, String renderedText, String renderedHtml) {}

    public enum RecentInboxUnavailableReason {
        NOT_CONNECTED,
        DISCONNECTED,
        NO_READ_GRANT,
        REVOKED,
        MESSAGE_NOT_FOUND,
        GMAIL_UNAVAILABLE,
        INVALID_CURSOR
    }

    public static class RecentInboxUnavailableException extends RuntimeException {

        private final RecentInboxUnavailableReason reason;

        public RecentInboxUnavailableException(RecentInboxUnavailableReason reason) {
            this(reason, null);
        }

        public RecentInboxUnavailableException(
                RecentInboxUnavailableReason reason, Throwable cause) {
            super("Gmail inbox is unavailable: " + reason, cause);
            this.reason = Objects.requireNonNull(reason, "reason must not be null");
        }

        public RecentInboxUnavailableReason reason() {
            return reason;
        }
    }

    private record InboxCursor(String pageToken, int loadedCount) {}

    private static final class InboxCursorCodec {

        private static final String VERSION = "v1";
        private static final String HMAC_ALGORITHM = "HmacSHA256";
        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

        private final SecretKeySpec signingKey;

        private InboxCursorCodec(String signingKeyBase64) {
            byte[] decodedSigningKey;
            try {
                decodedSigningKey = Base64.getDecoder().decode(signingKeyBase64);
            } catch (IllegalArgumentException invalidSigningKey) {
                throw new IllegalStateException("Invalid Gmail inbox cursor signing key");
            }
            try {
                this.signingKey = new SecretKeySpec(decodedSigningKey, HMAC_ALGORITHM);
            } finally {
                Arrays.fill(decodedSigningKey, (byte) 0);
            }
        }

        private InboxCursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return new InboxCursor(null, 0);
            }
            String payload;
            try {
                payload = new String(DECODER.decode(cursor.trim()), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException invalidBase64) {
                throw new RecentInboxUnavailableException(
                        RecentInboxUnavailableReason.INVALID_CURSOR, invalidBase64);
            }
            String[] parts = payload.split("\n", 4);
            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                throw new RecentInboxUnavailableException(
                        RecentInboxUnavailableReason.INVALID_CURSOR);
            }
            String unsignedPayload = parts[0] + "\n" + parts[1] + "\n" + parts[2];
            if (!validSignature(unsignedPayload, parts[3])) {
                throw new RecentInboxUnavailableException(
                        RecentInboxUnavailableReason.INVALID_CURSOR);
            }
            try {
                int loadedCount = Integer.parseInt(parts[1]);
                if (loadedCount < 0 || loadedCount > MAX_MESSAGES) {
                    throw new RecentInboxUnavailableException(
                            RecentInboxUnavailableReason.INVALID_CURSOR);
                }
                return new InboxCursor(parts[2].isBlank() ? null : parts[2], loadedCount);
            } catch (NumberFormatException invalidLoadedCount) {
                throw new RecentInboxUnavailableException(
                        RecentInboxUnavailableReason.INVALID_CURSOR, invalidLoadedCount);
            }
        }

        private String encode(String pageToken, int loadedCount) {
            String unsignedPayload = VERSION + "\n" + loadedCount + "\n" + pageToken;
            String payload = unsignedPayload + "\n" + signatureFor(unsignedPayload);
            return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        }

        private boolean validSignature(String payload, String actualSignature) {
            byte[] expectedSignatureBytes = signatureFor(payload).getBytes(StandardCharsets.UTF_8);
            byte[] actualSignatureBytes = actualSignature.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expectedSignatureBytes, actualSignatureBytes);
        }

        private String signatureFor(String payload) {
            try {
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(signingKey);
                return ENCODER.encodeToString(
                        mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            } catch (GeneralSecurityException cryptoException) {
                throw new IllegalStateException(
                        "Unable to sign Gmail inbox cursor", cryptoException);
            }
        }
    }

    /**
     * Idempotent lazy backfill enqueue. Triggers only when the tenant has never completed a full
     * sync (no row in {@code gmail_inbox_sync_state}, or {@code last_full_sync_at IS NULL}). The
     * enqueuer itself dedups via {@code processing_job} so a redundant call here is harmless.
     */
    private void enqueueBackfillIfFirstFetch(UUID tenantId) {
        if (needsFullSyncFirst(tenantId)) {
            inboxBackfillEnqueuer.enqueueIfNotPending(tenantId);
        }
    }
}
