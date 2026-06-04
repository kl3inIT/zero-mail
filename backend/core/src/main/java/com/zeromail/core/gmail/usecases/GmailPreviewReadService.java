package com.zeromail.core.gmail.usecases;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.model.Thread;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GmailMessageHeaders;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.persistence.lowlevel.MailMessageObservedReadRepository;
import com.zeromail.core.gmail.projection.ObservedPreviewMessage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GmailPreviewReadService {

    private static final Logger log = LoggerFactory.getLogger(GmailPreviewReadService.class);

    private static final List<String> METADATA_HEADERS =
            List.of(
                    "From",
                    "To",
                    "Cc",
                    "Subject",
                    "Message-ID",
                    "References",
                    "In-Reply-To",
                    "Reply-To",
                    "List-Unsubscribe",
                    "List-Unsubscribe-Post",
                    "List-Id",
                    "Precedence",
                    "Content-Type");
    private static final int LIST_UNSUBSCRIBE_URL_MAX_LENGTH = 2048;
    private static final int LIST_UNSUBSCRIBE_MAILTO_MAX_LENGTH = 512;
    private static final String LIST_UNSUBSCRIBE_ONE_CLICK_TRIGGER = "List-Unsubscribe=One-Click";
    private static final int SUBJECT_EXCERPT_MAX_LENGTH = 120;
    private static final String METADATA_FIELDS =
            "id,threadId,labelIds,internalDate,payload/headers,payload/parts/filename,"
                    + "payload/parts/mimeType";
    private static final String TRIAGE_METADATA_FIELDS =
            "id,threadId,labelIds,internalDate,payload/headers";
    private static final String RECENT_INBOX_LIST_FIELDS = "messages(id,threadId)";
    private static final String INBOX_LABEL_ID = "INBOX";
    private static final int RECENT_INBOX_MAX_MESSAGES = 100;
    private static final List<String> THREAD_DISPLAY_METADATA_HEADERS =
            List.of("From", "To", "Cc", "Subject");
    private static final String THREAD_DISPLAY_FIELDS =
            "id,messages(id,threadId,internalDate,snippet,payload/headers)";
    private static final int SNIPPET_EXCERPT_MAX_LENGTH = 200;
    private static final String FULL_FIELDS =
            "id,threadId,labelIds,internalDate,payload/headers,payload/body/size,"
                    + "payload/parts(filename,mimeType,body/size,parts)";

    private final MailMessageObservedReadRepository mailMessageObservedReadRepository;
    private final GmailConnectionRepository gmailConnectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;
    private final Clock clock;

    @Autowired
    public GmailPreviewReadService(
            MailMessageObservedReadRepository mailMessageObservedReadRepository,
            GmailConnectionRepository gmailConnectionRepository,
            GmailApiClientFactory gmailApiClientFactory,
            RefreshTokenCipher refreshTokenCipher) {
        this(
                mailMessageObservedReadRepository,
                gmailConnectionRepository,
                gmailApiClientFactory,
                refreshTokenCipher,
                Clock.systemUTC());
    }

    GmailPreviewReadService(
            MailMessageObservedReadRepository mailMessageObservedReadRepository,
            GmailConnectionRepository gmailConnectionRepository,
            GmailApiClientFactory gmailApiClientFactory,
            RefreshTokenCipher refreshTokenCipher,
            Clock clock) {
        this.mailMessageObservedReadRepository =
                Objects.requireNonNull(
                        mailMessageObservedReadRepository,
                        "mailMessageObservedReadRepository must not be null");
        this.gmailConnectionRepository =
                Objects.requireNonNull(
                        gmailConnectionRepository, "gmailConnectionRepository must not be null");
        this.gmailApiClientFactory =
                Objects.requireNonNull(
                        gmailApiClientFactory, "gmailApiClientFactory must not be null");
        this.refreshTokenCipher =
                Objects.requireNonNull(refreshTokenCipher, "refreshTokenCipher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public List<GmailPreviewMessage> fetchRecentMessages(
            UUID tenantId, int sampleSize, boolean includeBodyEvidence, Duration fetchBudget) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(fetchBudget, "fetchBudget must not be null");
        List<ObservedPreviewMessage> observedMessages =
                mailMessageObservedReadRepository.findRecentObservedMessages(tenantId, sampleSize);
        if (observedMessages.isEmpty()) {
            return List.of();
        }

        GmailConnectionEntity connection =
                gmailConnectionRepository
                        .findByTenantId(tenantId)
                        .orElseThrow(
                                () ->
                                        new GmailPreviewReadUnavailableException(
                                                UnavailableReason.NOT_CONNECTED));
        if (connection.getStatus() != GmailConnectionStatus.CONNECTED) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.DISCONNECTED);
        }
        if (connection.getRefreshTokenEncrypted() == null) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.NO_READ_GRANT);
        }

        try {
            Gmail gmail = buildPreviewReadClient(connection, tenantId, fetchBudget);
            return fetchMessagesWithinBudget(
                    gmail, observedMessages, includeBodyEvidence, fetchBudget);
        } catch (InvalidGrantException invalidGrantException) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.REVOKED);
        } catch (GoogleJsonResponseException googleResponseException) {
            if (googleResponseException.getStatusCode() == 401
                    || googleResponseException.getStatusCode() == 403) {
                throw new GmailPreviewReadUnavailableException(UnavailableReason.NO_READ_GRANT);
            }
            throw new GmailPreviewReadUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        } catch (IOException ioException) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        }
    }

    @Transactional(readOnly = true)
    public List<GmailPreviewMessage> fetchRecentInboxMessages(
            UUID tenantId, int sampleSize, boolean includeBodyEvidence, Duration fetchBudget) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(fetchBudget, "fetchBudget must not be null");

        GmailConnectionEntity connection =
                gmailConnectionRepository
                        .findByTenantId(tenantId)
                        .orElseThrow(
                                () ->
                                        new GmailPreviewReadUnavailableException(
                                                UnavailableReason.NOT_CONNECTED));
        if (connection.getStatus() != GmailConnectionStatus.CONNECTED) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.DISCONNECTED);
        }
        if (connection.getRefreshTokenEncrypted() == null) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.NO_READ_GRANT);
        }

        try {
            Gmail gmail = buildPreviewReadClient(connection, tenantId, fetchBudget);
            List<ObservedPreviewMessage> recentInboxMessages =
                    fetchRecentInboxMessageReferences(gmail, sampleSize);
            if (recentInboxMessages.isEmpty()) {
                return List.of();
            }
            return fetchMessagesWithinBudget(
                    gmail, recentInboxMessages, includeBodyEvidence, fetchBudget);
        } catch (InvalidGrantException invalidGrantException) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.REVOKED);
        } catch (GoogleJsonResponseException googleResponseException) {
            if (googleResponseException.getStatusCode() == 401
                    || googleResponseException.getStatusCode() == 403) {
                throw new GmailPreviewReadUnavailableException(UnavailableReason.NO_READ_GRANT);
            }
            throw new GmailPreviewReadUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        } catch (IOException ioException) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        }
    }

    private Gmail buildPreviewReadClient(
            GmailConnectionEntity connection, UUID tenantId, Duration requestTimeout)
            throws IOException {
        String decryptedRefreshToken =
                new String(
                        refreshTokenCipher.decrypt(
                                connection.getRefreshTokenEncrypted(), tenantId.toString()),
                        StandardCharsets.UTF_8);
        GmailApiClientFactory.TokenRefreshResult tokenResult =
                gmailApiClientFactory.refreshAccessToken(decryptedRefreshToken);
        return gmailApiClientFactory.buildGmailClient(
                tokenResult.accessToken().value(), requestTimeout);
    }

    private List<ObservedPreviewMessage> fetchRecentInboxMessageReferences(
            Gmail gmail, int requestedSampleSize) throws IOException {
        int sampleSize = Math.min(Math.max(requestedSampleSize, 0), RECENT_INBOX_MAX_MESSAGES);
        if (sampleSize == 0) {
            return List.of();
        }
        ListMessagesResponse listMessagesResponse =
                gmail.users()
                        .messages()
                        .list("me")
                        .setLabelIds(List.of(INBOX_LABEL_ID))
                        .setMaxResults((long) sampleSize)
                        .setFields(RECENT_INBOX_LIST_FIELDS)
                        .execute();
        List<Message> messageReferences =
                listMessagesResponse.getMessages() == null
                        ? List.of()
                        : listMessagesResponse.getMessages();
        Instant observedAt = clock.instant();
        ArrayList<ObservedPreviewMessage> recentInboxMessages = new ArrayList<>();
        for (Message messageReference : messageReferences) {
            if (messageReference == null || messageReference.getId() == null) {
                continue;
            }
            recentInboxMessages.add(
                    new ObservedPreviewMessage(
                            messageReference.getId(),
                            Objects.requireNonNullElse(
                                    messageReference.getThreadId(), messageReference.getId()),
                            new String[] {INBOX_LABEL_ID},
                            null,
                            observedAt));
        }
        return List.copyOf(recentInboxMessages);
    }

    @Transactional(readOnly = true)
    public Optional<GmailPreviewMessage> fetchTriageInput(
            UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");

        ObservedPreviewMessage observedMessage =
                new ObservedPreviewMessage(
                        gmailMessageId, gmailThreadId, new String[0], null, observedAt);
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(tenantId);
            Message gmailMessage = triageMessageGetRequest(gmail, gmailMessageId).execute();
            GmailPreviewMessage previewMessage =
                    toPreviewMessage(observedMessage, gmailMessage, false);
            log.info(
                    "event=triage_input_fetched tenantId={} gmailMessageId={}",
                    tenantId,
                    gmailMessageId);
            return Optional.of(previewMessage);
        } catch (GoogleJsonResponseException googleResponseException) {
            if (googleResponseException.getStatusCode() == 404) {
                log.info(
                        "event=triage_input_fetch_message_gone tenantId={} gmailMessageId={}",
                        tenantId,
                        gmailMessageId);
                return Optional.empty();
            }
            if (googleResponseException.getStatusCode() == 401
                    || googleResponseException.getStatusCode() == 403) {
                throw new GmailPreviewReadUnavailableException(UnavailableReason.NO_READ_GRANT);
            }
            throw new GmailPreviewReadUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        } catch (IOException ioException) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.GMAIL_UNAVAILABLE);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, GmailThreadDisplay> fetchThreadDisplays(
            UUID tenantId, List<String> gmailThreadIds, Duration fetchBudget) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(fetchBudget, "fetchBudget must not be null");
        List<String> requestedThreadIds =
                gmailThreadIds == null
                        ? List.of()
                        : gmailThreadIds.stream()
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(threadId -> !threadId.isBlank())
                                .distinct()
                                .toList();
        if (requestedThreadIds.isEmpty()) {
            return Map.of();
        }

        Optional<GmailConnectionEntity> optionalConnection =
                gmailConnectionRepository.findByTenantId(tenantId);
        if (optionalConnection.isEmpty()) {
            log.info(
                    "event=thread_display_fetch_skipped tenantId={} reason=not_connected",
                    tenantId);
            return Map.of();
        }
        GmailConnectionEntity connection = optionalConnection.orElseThrow();
        if (connection.getStatus() != GmailConnectionStatus.CONNECTED
                || connection.getRefreshTokenEncrypted() == null) {
            log.info(
                    "event=thread_display_fetch_skipped tenantId={} reason=disconnected", tenantId);
            return Map.of();
        }

        try {
            Gmail gmail = gmailApiClientFactory.buildClientForConnection(connection, tenantId);
            return fetchThreadDisplaysWithBatch(
                    gmail, requestedThreadIds, connection.getGoogleEmail(), fetchBudget);
        } catch (InvalidGrantException invalidGrantException) {
            log.warn("event=thread_display_fetch_failed tenantId={} reason=revoked", tenantId);
            return Map.of();
        } catch (IOException | GmailPreviewReadUnavailableException displayFetchException) {
            log.warn(
                    "event=thread_display_fetch_failed tenantId={} reason={}",
                    tenantId,
                    displayFetchException.getClass().getSimpleName());
            return Map.of();
        }
    }

    private Map<String, GmailThreadDisplay> fetchThreadDisplaysWithBatch(
            Gmail gmail, List<String> gmailThreadIds, String selfEmail, Duration fetchBudget)
            throws IOException {
        Instant deadline = clock.instant().plus(fetchBudget);
        BatchRequest batchRequest = gmail.batch();
        LinkedHashMap<String, GmailThreadDisplay> displaysByThreadId = new LinkedHashMap<>();

        for (String gmailThreadId : gmailThreadIds) {
            assertWithinBudget(deadline);
            JsonBatchCallback<Thread> callback =
                    new JsonBatchCallback<>() {
                        @Override
                        public void onSuccess(Thread gmailThread, HttpHeaders responseHeaders) {
                            toThreadDisplay(gmailThreadId, gmailThread, selfEmail)
                                    .ifPresent(
                                            display ->
                                                    displaysByThreadId.put(gmailThreadId, display));
                        }

                        @Override
                        public void onFailure(
                                GoogleJsonError googleJsonError, HttpHeaders responseHeaders) {
                            log.info(
                                    "event=thread_display_row_unavailable gmailThreadId={}",
                                    gmailThreadId);
                        }
                    };
            threadGetRequest(gmail, gmailThreadId).queue(batchRequest, callback);
        }
        batchRequest.execute();
        return Map.copyOf(displaysByThreadId);
    }

    private static Gmail.Users.Threads.Get threadGetRequest(Gmail gmail, String gmailThreadId)
            throws IOException {
        Gmail.Users.Threads.Get threadGetRequest =
                gmail.users()
                        .threads()
                        .get("me", gmailThreadId)
                        .setFormat("metadata")
                        .setFields(THREAD_DISPLAY_FIELDS);
        threadGetRequest.setMetadataHeaders(THREAD_DISPLAY_METADATA_HEADERS);
        return threadGetRequest;
    }

    private static Optional<GmailThreadDisplay> toThreadDisplay(
            String requestedThreadId, Thread gmailThread, String selfEmail) {
        if (gmailThread == null || gmailThread.getMessages() == null) {
            return Optional.empty();
        }
        Message latestMessage =
                gmailThread.getMessages().stream()
                        .filter(Objects::nonNull)
                        .max(
                                java.util.Comparator.comparingLong(
                                        message ->
                                                Objects.requireNonNullElse(
                                                        message.getInternalDate(), 0L)))
                        .orElse(null);
        if (latestMessage == null) {
            return Optional.empty();
        }
        MessagePart payload = latestMessage.getPayload();
        String subject = excerpt(GmailMessageHeaders.firstValue(payload, "Subject").orElse(""));
        String otherParty = otherParty(payload, selfEmail);
        Instant lastActivityAt = toInstant(latestMessage.getInternalDate());
        String snippet = snippetExcerpt(latestMessage.getSnippet());
        return Optional.of(
                new GmailThreadDisplay(
                        requestedThreadId,
                        subject,
                        otherParty,
                        lastActivityAt,
                        latestMessage.getId(),
                        snippet));
    }

    private static String otherParty(MessagePart payload, String selfEmail) {
        String normalizedSelfEmail = sanitizeEmail(selfEmail);
        List<String> participants =
                Stream.of(
                                GmailMessageHeaders.firstValue(payload, "From").orElse(""),
                                GmailMessageHeaders.firstValue(payload, "To").orElse(""),
                                GmailMessageHeaders.firstValue(payload, "Cc").orElse(""))
                        .flatMap(header -> parseRecipients(header).stream())
                        .filter(participant -> !participant.isBlank())
                        .filter(participant -> !participant.equals(normalizedSelfEmail))
                        .toList();
        return participants.isEmpty() ? null : participants.getFirst();
    }

    private List<GmailPreviewMessage> fetchMessagesWithinBudget(
            Gmail gmail,
            List<ObservedPreviewMessage> observedMessages,
            boolean includeBodyEvidence,
            Duration fetchBudget)
            throws IOException {
        Instant deadline = clock.instant().plus(fetchBudget);
        List<Message> gmailMessages;
        if (observedMessages.size() > 1) {
            try {
                gmailMessages =
                        fetchMessagesWithBatch(
                                gmail, observedMessages, includeBodyEvidence, deadline);
            } catch (IOException | RuntimeException batchException) {
                gmailMessages =
                        fetchMessagesSequentially(
                                gmail, observedMessages, includeBodyEvidence, deadline);
            }
        } else {
            gmailMessages =
                    fetchMessagesSequentially(
                            gmail, observedMessages, includeBodyEvidence, deadline);
        }
        return mergeObservedAndFetchedMessages(
                observedMessages, gmailMessages, includeBodyEvidence);
    }

    private List<Message> fetchMessagesWithBatch(
            Gmail gmail,
            List<ObservedPreviewMessage> observedMessages,
            boolean includeBodyEvidence,
            Instant deadline)
            throws IOException {
        BatchRequest batchRequest = gmail.batch();
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
                        // Missing individual messages fall back to the observed metadata row below.
                    }
                };

        for (ObservedPreviewMessage observedMessage : observedMessages) {
            assertWithinBudget(deadline);
            messageGetRequest(gmail, observedMessage.gmailMessageId(), includeBodyEvidence)
                    .queue(batchRequest, callback);
        }
        batchRequest.execute();

        ArrayList<Message> orderedMessages = new ArrayList<>();
        for (ObservedPreviewMessage observedMessage : observedMessages) {
            Message message = messagesById.get(observedMessage.gmailMessageId());
            if (message != null) {
                orderedMessages.add(message);
            }
        }
        return List.copyOf(orderedMessages);
    }

    private List<Message> fetchMessagesSequentially(
            Gmail gmail,
            List<ObservedPreviewMessage> observedMessages,
            boolean includeBodyEvidence,
            Instant deadline)
            throws IOException {
        ArrayList<Message> gmailMessages = new ArrayList<>();
        for (ObservedPreviewMessage observedMessage : observedMessages) {
            assertWithinBudget(deadline);
            gmailMessages.add(
                    messageGetRequest(gmail, observedMessage.gmailMessageId(), includeBodyEvidence)
                            .execute());
        }
        return List.copyOf(gmailMessages);
    }

    private static Gmail.Users.Messages.Get messageGetRequest(
            Gmail gmail, String gmailMessageId, boolean includeBodyEvidence) throws IOException {
        Gmail.Users.Messages.Get messageGetRequest =
                gmail.users()
                        .messages()
                        .get("me", gmailMessageId)
                        .setFormat(includeBodyEvidence ? "full" : "metadata")
                        .setFields(includeBodyEvidence ? FULL_FIELDS : METADATA_FIELDS);
        if (!includeBodyEvidence) {
            messageGetRequest.setMetadataHeaders(METADATA_HEADERS);
        }
        return messageGetRequest;
    }

    private static Gmail.Users.Messages.Get triageMessageGetRequest(
            Gmail gmail, String gmailMessageId) throws IOException {
        Gmail.Users.Messages.Get messageGetRequest =
                gmail.users()
                        .messages()
                        .get("me", gmailMessageId)
                        .setFormat("metadata")
                        .setFields(TRIAGE_METADATA_FIELDS);
        messageGetRequest.setMetadataHeaders(METADATA_HEADERS);
        return messageGetRequest;
    }

    private List<GmailPreviewMessage> mergeObservedAndFetchedMessages(
            List<ObservedPreviewMessage> observedMessages,
            List<Message> gmailMessages,
            boolean includeBodyEvidence) {
        Map<String, Message> messagesById = new LinkedHashMap<>();
        for (Message gmailMessage : gmailMessages) {
            if (gmailMessage != null && gmailMessage.getId() != null) {
                messagesById.put(gmailMessage.getId(), gmailMessage);
            }
        }

        ArrayList<GmailPreviewMessage> previewMessages = new ArrayList<>();
        for (ObservedPreviewMessage observedMessage : observedMessages) {
            Message gmailMessage = messagesById.get(observedMessage.gmailMessageId());
            previewMessages.add(
                    toPreviewMessage(observedMessage, gmailMessage, includeBodyEvidence));
        }
        return List.copyOf(previewMessages);
    }

    private GmailPreviewMessage toPreviewMessage(
            ObservedPreviewMessage observedMessage,
            Message gmailMessage,
            boolean includeBodyEvidence) {
        MessagePart payload = gmailMessage == null ? null : gmailMessage.getPayload();
        List<String> labelIds =
                gmailMessage == null || gmailMessage.getLabelIds() == null
                        ? List.of(observedMessage.labelIds())
                        : List.copyOf(gmailMessage.getLabelIds());
        String fromHeader = GmailMessageHeaders.firstValue(payload, "From").orElse("");
        String replyToHeader =
                GmailMessageHeaders.firstValue(payload, "Reply-To").orElse(fromHeader);
        String senderEmail = sanitizeEmail(extractEmailAddress(fromHeader));
        String senderName = extractDisplayName(fromHeader);
        String replyToAddress = sanitizeEmail(extractEmailAddress(replyToHeader));
        String senderDomain =
                senderEmail.contains("@")
                        ? senderEmail.substring(senderEmail.indexOf('@') + 1)
                        : "";
        String subjectExcerpt =
                excerpt(GmailMessageHeaders.firstValue(payload, "Subject").orElse(""));
        String rfcMessageId =
                sanitizedText(GmailMessageHeaders.firstValue(payload, "Message-ID").orElse(""));
        String references =
                sanitizedText(GmailMessageHeaders.firstValue(payload, "References").orElse(""));
        String inReplyTo =
                sanitizedText(GmailMessageHeaders.firstValue(payload, "In-Reply-To").orElse(""));
        List<String> toRecipients =
                parseRecipients(GmailMessageHeaders.firstValue(payload, "To").orElse(""));
        List<String> ccRecipients =
                parseRecipients(GmailMessageHeaders.firstValue(payload, "Cc").orElse(""));
        boolean hasAttachment = hasAttachment(payload);
        String listUnsubscribeHeaderValue =
                GmailMessageHeaders.firstValue(payload, "List-Unsubscribe").orElse(null);
        String listUnsubscribePostHeaderValue =
                GmailMessageHeaders.firstValue(payload, "List-Unsubscribe-Post").orElse(null);
        ListUnsubscribeExtraction listUnsubscribeExtraction =
                extractListUnsubscribe(listUnsubscribeHeaderValue, listUnsubscribePostHeaderValue);
        String listUnsubscribeUrl = listUnsubscribeExtraction.url();
        String listUnsubscribeMailto = listUnsubscribeExtraction.mailto();
        boolean listUnsubscribeOneClick = listUnsubscribeExtraction.oneClick();
        boolean listUnsubscribePresent =
                listUnsubscribeUrl != null || listUnsubscribeMailto != null;
        if (listUnsubscribeHeaderValue != null) {
            log.info(
                    "event=gmail_preview_list_unsubscribe_extracted gmailMessageId={} hasUrl={} hasMailto={} oneClick={}",
                    observedMessage.gmailMessageId(),
                    listUnsubscribeUrl != null,
                    listUnsubscribeMailto != null,
                    listUnsubscribeOneClick);
        }
        boolean newsletterIndicatorPresent =
                listUnsubscribePresent
                        || GmailMessageHeaders.firstValue(payload, "List-Id").isPresent()
                        || GmailMessageHeaders.firstValue(payload, "Precedence")
                                .map(
                                        value ->
                                                Set.of("bulk", "list")
                                                        .contains(
                                                                value.trim()
                                                                        .toLowerCase(Locale.ROOT)))
                                .orElse(false);
        Optional<Boolean> sanitizedBodyEvidencePresent =
                includeBodyEvidence ? Optional.of(hasBodyEvidence(payload)) : Optional.empty();
        Set<String> bodyDerivedFlags =
                includeBodyEvidence && sanitizedBodyEvidencePresent.orElse(false)
                        ? Set.of("body_evidence_present")
                        : Set.of();

        return new GmailPreviewMessage(
                observedMessage.gmailMessageId(),
                Objects.requireNonNullElse(
                        gmailMessage == null ? null : gmailMessage.getThreadId(),
                        observedMessage.gmailThreadId()),
                senderEmail,
                senderDomain,
                senderName,
                toRecipients,
                ccRecipients,
                subjectExcerpt,
                rfcMessageId,
                references,
                inReplyTo,
                replyToAddress,
                labelIds,
                gmailCategories(labelIds),
                toInstant(
                        gmailMessage == null
                                ? observedMessage.internalDate()
                                : gmailMessage.getInternalDate()),
                observedMessage.observedAt(),
                hasAttachment,
                listUnsubscribePresent,
                listUnsubscribeUrl,
                listUnsubscribeMailto,
                listUnsubscribeOneClick,
                newsletterIndicatorPresent,
                sanitizedBodyEvidencePresent,
                bodyDerivedFlags);
    }

    /**
     * Parse the RFC 2369 {@code List-Unsubscribe} header value into a structured (URL, mailto,
     * one-click) triple persisted by the ingest path on {@code
     * mail_message_observed.list_unsubscribe_url} / {@code list_unsubscribe_mailto} / {@code
     * list_unsubscribe_one_click} (changelog 041).
     *
     * <p>D-11 parse-time guard: HTTPS-only — any {@code http://} URI is dropped silently to
     * eliminate the downgrade attack surface at ingest time, so the DB never sees a plaintext
     * unsubscribe endpoint. The DB column has no scheme check (forward-only D-10) — this guard is
     * the only enforcement point.
     *
     * <p>RFC 8058 one-click flag: {@code listUnsubscribeOneClick} is {@code true} iff both the
     * HTTPS URL is present AND the {@code List-Unsubscribe-Post: List-Unsubscribe=One-Click} header
     * is present. A mailto-only sender with a {@code List-Unsubscribe-Post} header stays {@code
     * MAILTO} (one-click semantics requires the URL leg per RFC 8058).
     *
     * <p>Privacy: callers MUST NOT log {@link ListUnsubscribeExtraction#url()} or {@link
     * ListUnsubscribeExtraction#mailto()} values — they may carry the sender's canonical
     * unsubscribe endpoint plus opaque per-user tokens. Log boolean presence flags only ({@code
     * event=gmail_preview_list_unsubscribe_extracted}).
     */
    static ListUnsubscribeExtraction extractListUnsubscribe(
            String listUnsubscribeHeaderValue, String listUnsubscribePostHeaderValue) {
        if (listUnsubscribeHeaderValue == null || listUnsubscribeHeaderValue.isBlank()) {
            return ListUnsubscribeExtraction.empty();
        }
        String listUnsubscribeUrl = null;
        String listUnsubscribeMailto = null;
        for (String rawCandidateUri : listUnsubscribeHeaderValue.split(",")) {
            String candidateUri = stripAngleBrackets(rawCandidateUri.trim());
            if (candidateUri.isBlank()) {
                continue;
            }
            if (listUnsubscribeUrl == null && candidateUri.startsWith("https://")) {
                if (candidateUri.length() <= LIST_UNSUBSCRIBE_URL_MAX_LENGTH) {
                    listUnsubscribeUrl = candidateUri;
                }
                continue;
            }
            if (listUnsubscribeMailto == null && candidateUri.startsWith("mailto:")) {
                String parsedMailto = parseMailtoUri(candidateUri);
                if (parsedMailto != null
                        && parsedMailto.length() <= LIST_UNSUBSCRIBE_MAILTO_MAX_LENGTH) {
                    listUnsubscribeMailto = parsedMailto;
                }
                // D-11: http:// (and any other non-HTTPS, non-mailto scheme) is dropped silently.
            }
        }
        boolean listUnsubscribeOneClick =
                listUnsubscribeUrl != null
                        && listUnsubscribePostHeaderValue != null
                        && LIST_UNSUBSCRIBE_ONE_CLICK_TRIGGER.equalsIgnoreCase(
                                listUnsubscribePostHeaderValue.trim());
        return new ListUnsubscribeExtraction(
                listUnsubscribeUrl, listUnsubscribeMailto, listUnsubscribeOneClick);
    }

    private static String stripAngleBrackets(String candidateUri) {
        String stripped = candidateUri;
        if (stripped.startsWith("<")) {
            stripped = stripped.substring(1);
        }
        if (stripped.endsWith(">")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped.trim();
    }

    private static String parseMailtoUri(String candidateUri) {
        // D-23: use java.net.URI for structured parsing; reject malformed mailto: URIs so they
        // never reach the DB. Preserves the full original URI (including ?subject= and &body=
        // parameters) on success — downstream worker uses the parsed query string to populate
        // the Gmail-sent unsubscribe message.
        try {
            URI mailtoUri = new URI(candidateUri);
            if (!"mailto".equalsIgnoreCase(mailtoUri.getScheme())) {
                return null;
            }
            return candidateUri;
        } catch (URISyntaxException mailtoParseFailure) {
            return null;
        }
    }

    /**
     * Structured output of {@link #extractListUnsubscribe(String, String)}. Three nullable fields
     * map 1-to-1 to the {@code mail_message_observed.list_unsubscribe_*} columns shipped by
     * changelog 041.
     */
    public record ListUnsubscribeExtraction(String url, String mailto, boolean oneClick) {

        public static ListUnsubscribeExtraction empty() {
            return new ListUnsubscribeExtraction(null, null, false);
        }
    }

    private static List<String> parseRecipients(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return List.of();
        }
        ArrayList<String> recipients = new ArrayList<>();
        for (String part : headerValue.split(",")) {
            String emailAddress = sanitizeEmail(extractEmailAddress(part));
            if (!emailAddress.isBlank()) {
                recipients.add(emailAddress);
            }
        }
        return List.copyOf(recipients);
    }

    private static String extractEmailAddress(String headerValue) {
        if (headerValue == null) {
            return "";
        }
        int openAngleIndex = headerValue.indexOf('<');
        int closeAngleIndex = headerValue.indexOf('>');
        if (openAngleIndex >= 0 && closeAngleIndex > openAngleIndex) {
            return headerValue.substring(openAngleIndex + 1, closeAngleIndex);
        }
        return headerValue;
    }

    /**
     * Best-effort display-name extraction from a {@code From}-style header value (e.g. {@code
     * "John Doe" <john@x>} → {@code "John Doe"}). Returns {@code ""} when the header is
     * bare-address or unparseable. Result is sanitized + trimmed + length-capped at 320 chars to
     * match {@code mail_message_observed.sender_name}.
     */
    private static String extractDisplayName(String headerValue) {
        if (headerValue == null) {
            return "";
        }
        int openAngleIndex = headerValue.lastIndexOf('<');
        if (openAngleIndex <= 0) {
            return "";
        }
        String namePart = sanitizedText(headerValue.substring(0, openAngleIndex));
        if (namePart.length() >= 2
                && namePart.charAt(0) == '"'
                && namePart.charAt(namePart.length() - 1) == '"') {
            namePart = sanitizedText(namePart.substring(1, namePart.length() - 1));
        }
        if (namePart.length() > 320) {
            namePart = namePart.substring(0, 320);
        }
        return namePart;
    }

    private static String sanitizeEmail(String emailAddress) {
        return sanitizedText(emailAddress).toLowerCase(Locale.ROOT);
    }

    private static String excerpt(String subject) {
        String sanitizedSubject = sanitizedText(subject);
        if (sanitizedSubject.length() <= SUBJECT_EXCERPT_MAX_LENGTH) {
            return sanitizedSubject;
        }
        return sanitizedSubject.substring(0, SUBJECT_EXCERPT_MAX_LENGTH).trim();
    }

    private static String snippetExcerpt(String snippet) {
        String sanitizedSnippet = sanitizedText(snippet);
        if (sanitizedSnippet.length() <= SNIPPET_EXCERPT_MAX_LENGTH) {
            return sanitizedSnippet;
        }
        return sanitizedSnippet.substring(0, SNIPPET_EXCERPT_MAX_LENGTH).trim();
    }

    private static String sanitizedText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").replaceAll("\\s+", " ").trim();
    }

    private static List<String> gmailCategories(List<String> labelIds) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (String labelId : labelIds) {
            if (labelId != null && labelId.startsWith("CATEGORY_")) {
                categories.add(labelId.substring("CATEGORY_".length()).toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(categories);
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

    private static boolean hasBodyEvidence(MessagePart payload) {
        if (payload == null) {
            return false;
        }
        if (payload.getBody() != null
                && payload.getBody().getSize() != null
                && payload.getBody().getSize() > 0) {
            return true;
        }
        if (payload.getParts() == null) {
            return false;
        }
        for (MessagePart part : payload.getParts()) {
            if (hasBodyEvidence(part)) {
                return true;
            }
        }
        return false;
    }

    private static Instant toInstant(Long internalDateMillis) {
        return internalDateMillis == null
                ? Instant.EPOCH
                : Instant.ofEpochMilli(internalDateMillis);
    }

    private void assertWithinBudget(Instant deadline) {
        if (clock.instant().isAfter(deadline)) {
            throw new GmailPreviewReadUnavailableException(UnavailableReason.FETCH_TIMEOUT);
        }
    }

    public record GmailPreviewMessage(
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSenderEmail,
            String sanitizedSenderDomain,
            String sanitizedSenderName,
            List<String> sanitizedToRecipientEmails,
            List<String> sanitizedCcRecipientEmails,
            String sanitizedSubjectExcerpt,
            String rfcMessageId,
            String references,
            String inReplyTo,
            String replyToAddress,
            List<String> gmailLabelIds,
            List<String> gmailCategories,
            Instant internalDate,
            Instant observedAt,
            boolean hasAttachment,
            boolean listUnsubscribePresent,
            String listUnsubscribeUrl,
            String listUnsubscribeMailto,
            boolean listUnsubscribeOneClick,
            boolean newsletterIndicatorPresent,
            Optional<Boolean> sanitizedBodyEvidencePresent,
            Set<String> bodyDerivedFlags) {

        public GmailPreviewMessage {
            Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
            Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
            sanitizedSenderEmail = Objects.requireNonNullElse(sanitizedSenderEmail, "");
            sanitizedSenderDomain = Objects.requireNonNullElse(sanitizedSenderDomain, "");
            sanitizedSenderName = Objects.requireNonNullElse(sanitizedSenderName, "");
            sanitizedSubjectExcerpt = Objects.requireNonNullElse(sanitizedSubjectExcerpt, "");
            rfcMessageId = Objects.requireNonNullElse(rfcMessageId, "");
            references = Objects.requireNonNullElse(references, "");
            inReplyTo = Objects.requireNonNullElse(inReplyTo, "");
            replyToAddress = Objects.requireNonNullElse(replyToAddress, "");
            listUnsubscribeUrl =
                    listUnsubscribeUrl == null || listUnsubscribeUrl.isBlank()
                            ? null
                            : listUnsubscribeUrl;
            listUnsubscribeMailto =
                    listUnsubscribeMailto == null || listUnsubscribeMailto.isBlank()
                            ? null
                            : listUnsubscribeMailto;
            sanitizedToRecipientEmails =
                    List.copyOf(
                            Objects.requireNonNull(
                                    sanitizedToRecipientEmails,
                                    "sanitizedToRecipientEmails must not be null"));
            sanitizedCcRecipientEmails =
                    List.copyOf(
                            Objects.requireNonNull(
                                    sanitizedCcRecipientEmails,
                                    "sanitizedCcRecipientEmails must not be null"));
            gmailLabelIds =
                    List.copyOf(
                            Objects.requireNonNull(
                                    gmailLabelIds, "gmailLabelIds must not be null"));
            gmailCategories =
                    List.copyOf(
                            Objects.requireNonNull(
                                    gmailCategories, "gmailCategories must not be null"));
            Objects.requireNonNull(internalDate, "internalDate must not be null");
            Objects.requireNonNull(observedAt, "observedAt must not be null");
            sanitizedBodyEvidencePresent =
                    Objects.requireNonNullElseGet(sanitizedBodyEvidencePresent, Optional::empty);
            bodyDerivedFlags =
                    Set.copyOf(
                            Objects.requireNonNull(
                                    bodyDerivedFlags, "bodyDerivedFlags must not be null"));
        }
    }

    public record GmailThreadDisplay(
            String gmailThreadId,
            String subject,
            String otherParty,
            Instant lastActivityAt,
            String latestMessageId,
            String snippet) {

        public GmailThreadDisplay {
            Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
            subject = subject == null || subject.isBlank() ? null : subject;
            otherParty = otherParty == null || otherParty.isBlank() ? null : otherParty;
            latestMessageId =
                    latestMessageId == null || latestMessageId.isBlank() ? null : latestMessageId;
            snippet = snippet == null || snippet.isBlank() ? null : snippet;
        }
    }

    public enum UnavailableReason {
        NOT_CONNECTED,
        DISCONNECTED,
        NO_READ_GRANT,
        REVOKED,
        FETCH_TIMEOUT,
        GMAIL_UNAVAILABLE
    }

    public static class GmailPreviewReadUnavailableException extends RuntimeException {

        private final UnavailableReason reason;

        public GmailPreviewReadUnavailableException(UnavailableReason reason) {
            super("Gmail preview read unavailable: " + reason.name());
            this.reason = reason;
        }

        public UnavailableReason reason() {
            return reason;
        }
    }
}
