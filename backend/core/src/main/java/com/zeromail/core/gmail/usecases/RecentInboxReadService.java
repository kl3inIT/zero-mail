package com.zeromail.core.gmail.usecases;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.Thread;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.exception.MailboxDisconnectedException;
import com.zeromail.core.gmail.exception.MailboxNotOwnedException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GmailMessageHeaders;
import com.zeromail.core.inbox.domain.InboxProjectionDataSource;
import com.zeromail.core.inbox.domain.MessageClass;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateId;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository;
import com.zeromail.core.inbox.usecases.InboxBackfillEnqueuer;
import com.zeromail.core.inbox.usecases.InboxProjectionMessage;
import com.zeromail.core.inbox.usecases.InboxProjectionPage;
import com.zeromail.core.inbox.usecases.InboxProjectionReadService;
import com.zeromail.core.inbox.usecases.InvalidProjectionCursorException;
import com.zeromail.core.mailbox.MailboxContext;
import com.zeromail.core.mailbox.MailboxRef;
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
import java.util.Comparator;
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

    /**
     * The inbox list is lazy-unbounded: recent rows come from the projection, and deeper scrolls
     * continue through Gmail page tokens until Gmail has no next page. The wire contract still has
     * an integer {@code maxMessages}; use the largest int as an explicit "unknown upper bound"
     * sentinel for current clients.
     */
    public static final int UNKNOWN_MAX_MESSAGES = Integer.MAX_VALUE;

    /**
     * Source tag prefix for Wave 1 orchestrator cursors. Pagination must stay on the source that
     * issued the first page; the prefix lets {@link #fetchPage(UUID, String, int)} dispatch the
     * follow-up cursor without parsing the inner payload twice.
     */
    private static final String PROJECTION_CURSOR_PREFIX = "P";

    private static final String LIVE_GMAIL_CURSOR_PREFIX = "G";

    private static final int LIVE_GMAIL_SKIP_PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(RecentInboxReadService.class);

    private static final Duration GMAIL_REQUEST_TIMEOUT = Duration.ofSeconds(6);
    private static final int SUBJECT_MAX_LENGTH = 200;
    private static final int SNIPPET_MAX_LENGTH = 240;
    private static final int HEADER_MAX_LENGTH = 500;
    private static final int RENDERED_TEXT_MAX_LENGTH = 12_000;
    private static final int RENDERED_HTML_MAX_LENGTH = 200_000;
    private static final int INLINE_IMAGE_MAX_BYTES = 1_000_000;
    private static final int INLINE_IMAGE_TOTAL_MAX_BYTES = 3_000_000;
    private static final int BATCH_CHUNK_SIZE = 50;
    private static final List<String> METADATA_HEADERS = List.of("From", "To", "Cc", "Subject");
    private static final String MESSAGE_LIST_FIELDS = "messages(id,threadId),nextPageToken";
    private static final String METADATA_FIELDS =
            "id,threadId,labelIds,internalDate,snippet,payload/headers,payload/parts/filename,"
                    + "payload/parts/parts/filename";
    private static final String FULL_FIELDS = "id,threadId,labelIds,internalDate,snippet,payload";
    private static final String THREAD_FULL_FIELDS =
            "id,messages(id,threadId,labelIds,internalDate,snippet,payload)";

    private final GmailApiClientFactory gmailApiClientFactory;
    private final SafeHtmlSanitizer safeHtmlSanitizer;
    private final InboxCursorCodec inboxCursorCodec;
    private final InboxBackfillEnqueuer inboxBackfillEnqueuer;
    private final GmailInboxSyncStateRepository inboxSyncStateRepository;
    private final InboxProjectionReadService inboxProjectionReadService;

    public RecentInboxReadService(
            GmailApiClientFactory gmailApiClientFactory,
            CryptoProperties cryptoProperties,
            SafeHtmlSanitizer safeHtmlSanitizer,
            InboxBackfillEnqueuer inboxBackfillEnqueuer,
            GmailInboxSyncStateRepository inboxSyncStateRepository,
            InboxProjectionReadService inboxProjectionReadService) {
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
                        inboxProjectionReadService, "inboxProjectionReadService must not be null");
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
    public RecentInboxPage fetchPage(UUID tenantId, String cursor, int requestedLimit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (cursor != null && !cursor.isBlank()) {
            String trimmedCursor = cursor.trim();
            String prefix = trimmedCursor.substring(0, 1);
            String innerCursor = trimmedCursor.substring(1);
            return switch (prefix) {
                case PROJECTION_CURSOR_PREFIX -> {
                    ProjectionCursorEnvelope projectionCursorEnvelope =
                            decodeProjectionCursorEnvelope(innerCursor);
                    yield fetchPageFromProjectionOrLiveGmail(
                            tenantId,
                            projectionCursorEnvelope.innerCursor(),
                            requestedLimit,
                            projectionCursorEnvelope.loadedBefore(),
                            "projection_cursor_unavailable");
                }
                case LIVE_GMAIL_CURSOR_PREFIX ->
                        fetchPageFromLiveGmail(tenantId, innerCursor, requestedLimit);
                default ->
                        throw new RecentInboxUnavailableException(
                                RecentInboxUnavailableReason.INVALID_CURSOR);
            };
        }

        Optional<MailboxRef> currentMailboxRef = activeMailboxRef(tenantId);
        // Lazy backfill trigger (Phase A wave 3): the first time a tenant fetches the inbox after
        // connecting, kick off an asynchronous backfill so the projection is ready by the time
        // Phase B swaps the read path to the DB. Live Gmail still serves the fallback response;
        // enqueue is idempotent via processing_job dedup so concurrent fetches do not stack jobs.
        enqueueBackfillIfFirstFetch(currentMailboxRef);

        if (needsFullSyncFirst(currentMailboxRef)) {
            // First connect: the background backfill (enqueued above) is still populating the
            // projection. Don't park the user behind an empty SYNCING banner waiting for the whole
            // backfill to finish — serve the live Gmail first page immediately (batched, ~1-2s) so
            // they see real mail right away. Subsequent visits read the fast DB projection once the
            // backfill has completed. Mirrors Inbox Zero, which always serves the list live and
            // treats the DB as a cache.
            log.info(
                    "event=inbox_read_first_connect_live tenantId={} gmailConnectionId={}",
                    tenantId,
                    currentMailboxRef.map(MailboxRef::gmailConnectionId).orElse(null));
            return fetchPageFromLiveGmail(tenantId, null, requestedLimit);
        }

        int firstPageLimit = effectiveLimit(requestedLimit);
        RecentInboxPage projectionPage =
                fetchPageFromProjectionOrLiveGmail(
                        tenantId, null, requestedLimit, 0, "projection_first_page_unavailable");
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

    private RecentInboxPage fetchPageFromProjectionOrLiveGmail(
            UUID tenantId,
            String innerCursor,
            int requestedLimit,
            int loadedBefore,
            String fallbackReason) {
        try {
            return fetchPageFromProjection(tenantId, innerCursor, requestedLimit, loadedBefore);
        } catch (IllegalStateException projectionFailure) {
            log.warn(
                    "event=inbox_read_projection_fallback tenantId={} reason={} exception={}",
                    tenantId,
                    fallbackReason,
                    projectionFailure.getClass().getSimpleName());
            return fetchPageFromLiveGmail(
                    tenantId, inboxCursorCodec.encode(null, loadedBefore), requestedLimit);
        }
    }

    private RecentInboxPage fetchPageFromProjection(
            UUID tenantId, String innerCursor, int requestedLimit, int loadedBefore) {
        // Scope the projection read to the ACTIVE mailbox. Without this the query returns every
        // mailbox's projection rows for the tenant, leaking one mailbox's inbox into another after
        // a switch. The live-Gmail fallback was already mailbox-scoped via gmailForActiveMailbox.
        UUID gmailConnectionId =
                activeMailboxRef(tenantId)
                        .map(MailboxRef::gmailConnectionId)
                        .orElseThrow(
                                () ->
                                        new RecentInboxUnavailableException(
                                                RecentInboxUnavailableReason.NOT_CONNECTED));
        InboxProjectionPage projectionPage;
        try {
            projectionPage =
                    inboxProjectionReadService.fetchInboxPage(
                            tenantId, gmailConnectionId, innerCursor, requestedLimit);
        } catch (InvalidProjectionCursorException invalidProjectionCursorException) {
            throw new RecentInboxUnavailableException(
                    RecentInboxUnavailableReason.INVALID_CURSOR, invalidProjectionCursorException);
        }
        Map<String, String> labelNamesById =
                requiresCustomLabelNameLookup(projectionPage.items())
                        ? fetchLabelNamesByIdBestEffort(tenantId)
                        : Map.of();
        List<RecentInboxMessage> messages =
                toRecentInboxMessages(projectionPage.items(), labelNamesById);
        int loadedAfter = addLoadedCount(loadedBefore, messages.size());
        String nextCursor =
                projectionPage.nextCursor() == null
                        ? LIVE_GMAIL_CURSOR_PREFIX + inboxCursorCodec.encode(null, loadedAfter)
                        : PROJECTION_CURSOR_PREFIX
                                + encodeProjectionCursorEnvelope(
                                        projectionPage.nextCursor(), loadedAfter);
        if (messages.isEmpty() && projectionPage.nextCursor() == null && loadedBefore > 0) {
            return fetchPageFromLiveGmail(
                    tenantId, inboxCursorCodec.encode(null, loadedBefore), requestedLimit);
        }
        return new RecentInboxPage(
                messages,
                nextCursor,
                loadedAfter,
                UNKNOWN_MAX_MESSAGES,
                projectionPage.dataSource());
    }

    private RecentInboxPage fetchPageFromLiveGmail(
            UUID tenantId, String innerCursor, int requestedLimit) {
        InboxCursor inboxCursor = inboxCursorCodec.decode(innerCursor);
        int gmailPageLimit = effectiveLimit(requestedLimit);
        try {
            Gmail gmail = gmailForActiveMailbox(tenantId);
            LiveGmailListPage listPage = listLiveGmailPage(gmail, inboxCursor, gmailPageLimit);
            Map<String, String> labelNamesById = fetchLabelNamesById(gmail);
            List<RecentInboxMessage> messages =
                    fetchMessageMetadata(gmail, listPage.messageReferences(), labelNamesById);
            int loadedCount = addLoadedCount(inboxCursor.loadedCount(), messages.size());
            String nextCursor =
                    listPage.nextPageToken() == null
                            ? null
                            : LIVE_GMAIL_CURSOR_PREFIX
                                    + inboxCursorCodec.encode(
                                            listPage.nextPageToken(), loadedCount);
            return new RecentInboxPage(
                    messages,
                    nextCursor,
                    loadedCount,
                    UNKNOWN_MAX_MESSAGES,
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

    private LiveGmailListPage listLiveGmailPage(Gmail gmail, InboxCursor inboxCursor, int pageLimit)
            throws IOException {
        String pageToken = inboxCursor.pageToken();
        int remainingToSkip = pageToken == null ? inboxCursor.loadedCount() : 0;
        while (remainingToSkip > 0) {
            int skipPageSize = Math.min(remainingToSkip, LIVE_GMAIL_SKIP_PAGE_SIZE);
            ListMessagesResponse skipResponse =
                    listLiveGmailReferences(gmail, pageToken, skipPageSize);
            List<Message> skippedMessages =
                    skipResponse.getMessages() == null ? List.of() : skipResponse.getMessages();
            remainingToSkip -= skippedMessages.size();
            pageToken = skipResponse.getNextPageToken();
            if (pageToken == null || skippedMessages.isEmpty()) {
                return new LiveGmailListPage(List.of(), null);
            }
        }

        ListMessagesResponse listResponse = listLiveGmailReferences(gmail, pageToken, pageLimit);
        List<Message> messageReferences =
                listResponse.getMessages() == null ? List.of() : listResponse.getMessages();
        return new LiveGmailListPage(
                List.copyOf(messageReferences), listResponse.getNextPageToken());
    }

    private static ListMessagesResponse listLiveGmailReferences(
            Gmail gmail, String pageToken, int pageLimit) throws IOException {
        return gmail.users()
                .messages()
                .list("me")
                .setLabelIds(List.of("INBOX"))
                .setMaxResults((long) pageLimit)
                .setPageToken(pageToken)
                .setFields(MESSAGE_LIST_FIELDS)
                .execute();
    }

    private Map<String, String> fetchLabelNamesByIdBestEffort(UUID tenantId) {
        try {
            return fetchLabelNamesById(gmailForActiveMailbox(tenantId));
        } catch (RuntimeException | IOException labelLookupFailure) {
            log.info(
                    "event=inbox_projection_label_lookup_skipped tenantId={} failure={}",
                    tenantId,
                    labelLookupFailure.getClass().getSimpleName());
            return Map.of();
        }
    }

    private static boolean requiresCustomLabelNameLookup(
            List<InboxProjectionMessage> projectionItems) {
        for (InboxProjectionMessage projectionItem : projectionItems) {
            for (String labelId : projectionItem.labelIds()) {
                if (labelId != null && !labelId.isBlank() && !isSystemLabelId(labelId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSystemLabelId(String labelId) {
        return switch (labelId) {
            case "INBOX",
                    "UNREAD",
                    "SENT",
                    "DRAFT",
                    "SPAM",
                    "TRASH",
                    "IMPORTANT",
                    "STARRED",
                    "CATEGORY_PERSONAL",
                    "CATEGORY_SOCIAL",
                    "CATEGORY_PROMOTIONS",
                    "CATEGORY_UPDATES",
                    "CATEGORY_FORUMS" ->
                    true;
            default -> false;
        };
    }

    private static List<RecentInboxMessage> toRecentInboxMessages(
            List<InboxProjectionMessage> projectionItems, Map<String, String> labelNamesById) {
        ArrayList<RecentInboxMessage> recentInboxMessages = new ArrayList<>(projectionItems.size());
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
                            labelsFor(projectionItem.labelIds(), labelNamesById),
                            projectionItem.unread(),
                            projectionItem.hasAttachment(),
                            projectionItem.messageClass().orElse(null),
                            projectionItem.eventDt().orElse(null)));
        }
        return List.copyOf(recentInboxMessages);
    }

    /**
     * Freshness gate for the first-page orchestrator branch. Returns true when the tenant has not
     * yet completed a full sync — equivalent to "no row in sync_state OR last_full_sync_at IS
     * NULL".
     */
    private boolean needsFullSyncFirst(Optional<MailboxRef> mailboxRef) {
        return mailboxRef.map(this::needsFullSyncFirst).orElse(true);
    }

    private boolean needsFullSyncFirst(MailboxRef mailboxRef) {
        return inboxSyncStateRepository
                .findById(
                        new GmailInboxSyncStateId(
                                mailboxRef.tenantId(), mailboxRef.gmailConnectionId()))
                .map(syncState -> syncState.getLastFullSyncAt() == null)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public RecentInboxMessageDetail fetchMessageDetail(UUID tenantId, String gmailMessageId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        String messageId = requireMessageId(gmailMessageId);
        try {
            Gmail gmail = gmailForActiveMailbox(tenantId);
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
            Gmail gmail = gmailForActiveMailbox(tenantId);
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

    /**
     * Fetch a whole Gmail conversation (every message in the thread, including the tenant's own
     * SENT replies) and render each one, oldest-first — the conversation view the inbox reader
     * shows so a user can see at a glance that they already replied. Bodies are rendered live and
     * never persisted, mirroring {@link #fetchMessageDetail} (privacy: transient render only).
     *
     * @param gmailThreadId Gmail thread id selected by the reader.
     */
    @Transactional(readOnly = true)
    public RecentInboxThreadDetail fetchThreadDetail(UUID tenantId, String gmailThreadId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        String threadId = requireThreadId(gmailThreadId);
        try {
            Gmail gmail = gmailForActiveMailbox(tenantId);
            Thread thread =
                    gmail.users()
                            .threads()
                            .get("me", threadId)
                            .setFormat("full")
                            .setFields(THREAD_FULL_FIELDS)
                            .execute();
            List<Message> threadMessages =
                    thread.getMessages() == null ? List.of() : thread.getMessages();
            if (threadMessages.isEmpty()) {
                throw new RecentInboxUnavailableException(
                        RecentInboxUnavailableReason.MESSAGE_NOT_FOUND);
            }
            // Fetch the label catalogue once and reuse it for every message in the thread instead
            // of one Gmail labels.list per rendered message.
            Map<String, String> labelNamesById = fetchLabelNamesById(gmail);
            List<Message> orderedOldestFirst =
                    threadMessages.stream()
                            .sorted(
                                    Comparator.comparingLong(
                                            RecentInboxReadService::internalDateOf))
                            .toList();
            ArrayList<RecentInboxMessageDetail> renderedMessages =
                    new ArrayList<>(orderedOldestFirst.size());
            String threadSubject = null;
            for (Message threadMessage : orderedOldestFirst) {
                // Skip the thread's unsent draft. Gmail's threads.get returns the in-progress
                // reply draft as a thread message; rendering it would show a near-identical second
                // copy of the reply alongside the actually-sent message right after a send (the
                // composer autosaves a draft, the send fires, and the post-send thread refetch can
                // race ahead of the draft cleanup). A draft is the compose buffer, not part of the
                // read conversation, so it never belongs in the reader.
                if (threadMessage.getLabelIds() != null
                        && threadMessage.getLabelIds().contains("DRAFT")) {
                    continue;
                }
                RecentInboxMessageDetail renderedMessage =
                        renderMessageDetail(gmail, threadMessage, labelNamesById);
                if (threadSubject == null && renderedMessage.message().subject() != null) {
                    threadSubject = renderedMessage.message().subject();
                }
                renderedMessages.add(renderedMessage);
            }
            return new RecentInboxThreadDetail(
                    threadId,
                    threadSubject == null ? "" : threadSubject,
                    List.copyOf(renderedMessages));
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

    private static long internalDateOf(Message gmailMessage) {
        return gmailMessage.getInternalDate() == null ? 0L : gmailMessage.getInternalDate();
    }

    private RecentInboxMessageDetail renderMessageDetail(Gmail gmail, Message gmailMessage)
            throws IOException {
        return renderMessageDetail(gmail, gmailMessage, fetchLabelNamesById(gmail));
    }

    private RecentInboxMessageDetail renderMessageDetail(
            Gmail gmail, Message gmailMessage, Map<String, String> labelNamesById)
            throws IOException {
        String messageId = gmailMessage.getId();
        RecentInboxMessage message = toRecentInboxMessage(gmailMessage, labelNamesById);
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
            Gmail gmail = gmailForActiveMailbox(tenantId);
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

    private Optional<MailboxRef> activeMailboxRef(UUID tenantId) {
        return MailboxContext.currentOptional()
                .map(gmailConnectionId -> new MailboxRef(tenantId, gmailConnectionId));
    }

    private Gmail gmailForActiveMailbox(UUID tenantId) throws IOException {
        MailboxRef mailboxRef =
                activeMailboxRef(tenantId)
                        .orElseThrow(
                                () ->
                                        new RecentInboxUnavailableException(
                                                RecentInboxUnavailableReason.NOT_CONNECTED));
        try {
            return gmailApiClientFactory.buildClientForMailbox(mailboxRef, GMAIL_REQUEST_TIMEOUT);
        } catch (MailboxDisconnectedException mailboxDisconnectedException) {
            throw new RecentInboxUnavailableException(RecentInboxUnavailableReason.DISCONNECTED);
        } catch (MailboxNotOwnedException mailboxNotOwnedException) {
            throw new RecentInboxUnavailableException(RecentInboxUnavailableReason.NOT_CONNECTED);
        }
    }

    private static int effectiveLimit(int requestedLimit) {
        int positiveLimit = requestedLimit < 1 ? DEFAULT_PAGE_SIZE : requestedLimit;
        return Math.min(positiveLimit, MAX_PAGE_SIZE);
    }

    private ProjectionCursorEnvelope decodeProjectionCursorEnvelope(String innerCursor) {
        if (innerCursor == null || innerCursor.isBlank()) {
            return new ProjectionCursorEnvelope(null, 0);
        }
        try {
            InboxCursor projectionCursorEnvelope = inboxCursorCodec.decode(innerCursor);
            if (projectionCursorEnvelope.pageToken() == null
                    || projectionCursorEnvelope.pageToken().isBlank()) {
                throw new RecentInboxUnavailableException(
                        RecentInboxUnavailableReason.INVALID_CURSOR);
            }
            return new ProjectionCursorEnvelope(
                    projectionCursorEnvelope.pageToken(), projectionCursorEnvelope.loadedCount());
        } catch (RecentInboxUnavailableException invalidSignedEnvelope) {
            // Legacy P cursor from an older frontend session. Keep it usable, but it cannot carry
            // a cumulative loaded count, so a projection-to-Gmail transition may restart from the
            // top. New cursors always use the signed envelope above.
            return new ProjectionCursorEnvelope(innerCursor, 0);
        }
    }

    private String encodeProjectionCursorEnvelope(String innerCursor, int loadedCount) {
        if (innerCursor == null || innerCursor.isBlank()) {
            throw new IllegalArgumentException("innerCursor must not be blank");
        }
        if (loadedCount < 0) {
            throw new IllegalArgumentException("loadedCount must not be negative");
        }
        return inboxCursorCodec.encode(innerCursor, loadedCount);
    }

    private static int addLoadedCount(int loadedBefore, int pageSize) {
        try {
            return Math.addExact(loadedBefore, pageSize);
        } catch (ArithmeticException arithmeticException) {
            throw new RecentInboxUnavailableException(
                    RecentInboxUnavailableReason.INVALID_CURSOR, arithmeticException);
        }
    }

    private static String requireMessageId(String gmailMessageId) {
        if (gmailMessageId == null || gmailMessageId.isBlank()) {
            throw new IllegalArgumentException("gmailMessageId must not be blank");
        }
        return gmailMessageId.trim();
    }

    private static String requireThreadId(String gmailThreadId) {
        if (gmailThreadId == null || gmailThreadId.isBlank()) {
            throw new IllegalArgumentException("gmailThreadId must not be blank");
        }
        return gmailThreadId.trim();
    }

    private static List<RecentInboxMessage> fetchMessageMetadata(
            Gmail gmail, List<Message> messageReferences, Map<String, String> labelNamesById)
            throws IOException {
        Map<String, Message> messagesById = fetchMessagesWithBatch(gmail, messageReferences);
        ArrayList<RecentInboxMessage> messages = new ArrayList<>(messageReferences.size());
        // Iterate the references (not the map) to preserve Gmail's newest-first ordering.
        for (Message messageReference : messageReferences) {
            if (messageReference == null || messageReference.getId() == null) {
                continue;
            }
            Message gmailMessage = messagesById.get(messageReference.getId());
            if (gmailMessage == null) {
                continue;
            }
            messages.add(toRecentInboxMessage(gmailMessage, labelNamesById));
        }
        return List.copyOf(messages);
    }

    /**
     * Fetch metadata for every referenced message via Gmail batch requests (chunks of {@value
     * #BATCH_CHUNK_SIZE}) instead of one sequential {@code .get()} per message — the live-Gmail
     * fallback's N+1 was the residual cold-inbox latency once the projection backfill itself was
     * batched. Mirrors {@code InboxBackfillService#fetchMessagesWithBatch}. A message whose
     * individual sub-request fails is omitted (skipped this page; a later delta/backfill picks it
     * up). An {@link IOException} from a batch execute propagates to the caller's catch.
     */
    private static Map<String, Message> fetchMessagesWithBatch(
            Gmail gmail, List<Message> messageReferences) throws IOException {
        LinkedHashMap<String, Message> messagesById = new LinkedHashMap<>();
        JsonBatchCallback<Message> callback =
                new JsonBatchCallback<>() {
                    @Override
                    public void onSuccess(Message message, HttpHeaders responseHeaders) {
                        if (message != null && message.getId() != null) {
                            messagesById.put(message.getId(), message);
                        }
                    }

                    @Override
                    public void onFailure(
                            GoogleJsonError googleJsonError, HttpHeaders responseHeaders) {
                        // Individual message failures are non-fatal: that row is skipped this page.
                    }
                };

        for (int start = 0; start < messageReferences.size(); start += BATCH_CHUNK_SIZE) {
            List<Message> chunk =
                    messageReferences.subList(
                            start, Math.min(start + BATCH_CHUNK_SIZE, messageReferences.size()));
            BatchRequest batchRequest = gmail.batch();
            int queued = 0;
            for (Message reference : chunk) {
                if (reference == null || reference.getId() == null) {
                    continue;
                }
                gmail.users()
                        .messages()
                        .get("me", reference.getId())
                        .setFormat("metadata")
                        .setMetadataHeaders(METADATA_HEADERS)
                        .setFields(METADATA_FIELDS)
                        .queue(batchRequest, callback);
                queued++;
            }
            if (queued > 0) {
                batchRequest.execute();
            }
        }
        return messagesById;
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
                hasAttachment(payload),
                // live-Gmail path has no projection columns — messageClass + eventDt are null here;
                // calendar badges activate once backfill populates the projection
                // (message_class / event_dt). Never infer classification from live message content.
                null,
                null);
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
         * full sync. The frontend renders a "đang đồng bộ" banner instead of the inbox list (Wave
         * 3).
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
            boolean hasAttachment,
            MessageClass messageClass,
            Instant eventDt) {

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

    /**
     * A full conversation: every message in a Gmail thread (received + the tenant's own sent
     * replies), rendered oldest-first for the inbox conversation reader.
     */
    public record RecentInboxThreadDetail(
            String gmailThreadId, String subject, List<RecentInboxMessageDetail> messages) {

        public RecentInboxThreadDetail {
            messages = List.copyOf(messages);
        }
    }

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

    private record LiveGmailListPage(List<Message> messageReferences, String nextPageToken) {

        private LiveGmailListPage {
            messageReferences = List.copyOf(messageReferences);
        }
    }

    private record ProjectionCursorEnvelope(String innerCursor, int loadedBefore) {}

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
                if (loadedCount < 0) {
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
            String safePageToken = pageToken == null ? "" : pageToken;
            String unsignedPayload = VERSION + "\n" + loadedCount + "\n" + safePageToken;
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
    private void enqueueBackfillIfFirstFetch(Optional<MailboxRef> mailboxRef) {
        if (mailboxRef.isPresent() && needsFullSyncFirst(mailboxRef.get())) {
            inboxBackfillEnqueuer.enqueueIfNotPending(
                    mailboxRef.get().tenantId(), mailboxRef.get().gmailConnectionId());
        }
    }
}
