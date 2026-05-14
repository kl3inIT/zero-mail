package com.zeromail.api.e2estub;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.google.api.services.gmail.model.WatchRequest;
import com.google.api.services.gmail.model.WatchResponse;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.shared.privacy.Sensitive;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("e2e-stub")
@Primary
@ConditionalOnProperty(name = "zeromail.e2e-stub.enabled", havingValue = "true")
public class E2eStubGmailApiClientFactory extends GmailApiClientFactory {

    private static final String USER_ID = "me";
    private static final String INBOX_LABEL_ID = "INBOX";
    private static final String SENT_LABEL_ID = "SENT";
    private static final String E2E_STUB_ACCESS_TOKEN = "e2e-stub-access-token";

    private final ConcurrentHashMap<String, SeededMessage> seededMessages =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SeededDraft> seededDrafts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Label> seededLabels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedHashSet<String>> seededMessageLabelIds =
            new ConcurrentHashMap<>();
    private final AtomicLong draftSequence = new AtomicLong();
    private final AtomicLong labelSequence = new AtomicLong();

    public E2eStubGmailApiClientFactory(
            @Value("${spring.security.oauth2.client.registration.google.client-id}")
                    String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}")
                    String clientSecret,
            ZeroMailCoreProperties properties,
            GmailConnectionRepository gmailConnectionRepository,
            RefreshTokenCipher refreshTokenCipher) {
        super(clientId, clientSecret, properties, gmailConnectionRepository, refreshTokenCipher);
        seedSystemLabels();
    }

    public record SeededMessage(
            String tenantId,
            String messageId,
            String threadId,
            String from,
            String subject,
            String body) {}

    public record SeededDraft(String draftId, String messageId, String body) {}

    public void seedMessage(SeedMessageRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        SeededMessage seededMessage =
                new SeededMessage(
                        request.tenantId(),
                        request.messageId(),
                        request.threadId(),
                        request.from(),
                        request.subject(),
                        request.body());
        seededMessages.put(request.messageId(), seededMessage);
        seededMessageLabelIds.put(
                request.messageId(), new LinkedHashSet<>(List.of(INBOX_LABEL_ID)));
    }

    public void reset() {
        seededMessages.clear();
        seededDrafts.clear();
        seededLabels.clear();
        seededMessageLabelIds.clear();
        draftSequence.set(0L);
        labelSequence.set(0L);
        seedSystemLabels();
    }

    public SeededDraft findDraft(String messageId) {
        return seededDrafts.values().stream()
                .filter(seededDraft -> seededDraft.messageId().equals(messageId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Gmail buildGmailClient(String accessToken) {
        return new OfflineGmail(this);
    }

    @Override
    public Gmail buildGmailClient(String accessToken, Duration requestTimeout) {
        return new OfflineGmail(this);
    }

    @Override
    public Gmail buildClientForTenant(UUID tenantId) {
        return new OfflineGmail(this);
    }

    @Override
    public Gmail buildClientForTenant(UUID tenantId, Duration requestTimeout) {
        return new OfflineGmail(this);
    }

    @Override
    public Gmail buildClientForConnection(GmailConnectionEntity gmailConnection, UUID tenantId) {
        return new OfflineGmail(this);
    }

    @Override
    public Gmail buildClientForConnection(
            GmailConnectionEntity gmailConnection, UUID tenantId, Duration requestTimeout) {
        return new OfflineGmail(this);
    }

    @Override
    public TokenRefreshResult refreshAccessToken(String decryptedRefreshToken) {
        return new TokenRefreshResult(
                Sensitive.of(E2E_STUB_ACCESS_TOKEN), Instant.now().plus(Duration.ofHours(1)));
    }

    private void seedSystemLabels() {
        seededLabels.put(INBOX_LABEL_ID, label(INBOX_LABEL_ID, INBOX_LABEL_ID));
        seededLabels.put(SENT_LABEL_ID, label(SENT_LABEL_ID, SENT_LABEL_ID));
    }

    private Message toGmailMessage(String messageId) {
        SeededMessage seededMessage = seededMessages.get(messageId);
        if (seededMessage == null) {
            return new Message().setId(messageId).setThreadId(messageId).setLabelIds(List.of());
        }
        return new Message()
                .setId(seededMessage.messageId())
                .setThreadId(seededMessage.threadId())
                .setLabelIds(labelsForMessage(messageId))
                .setInternalDate(System.currentTimeMillis())
                .setPayload(messagePayload(seededMessage));
    }

    private List<String> labelsForMessage(String messageId) {
        LinkedHashSet<String> labelIds =
                seededMessageLabelIds.computeIfAbsent(
                        messageId, _ -> new LinkedHashSet<>(List.of(INBOX_LABEL_ID)));
        return List.copyOf(labelIds);
    }

    private MessagePart messagePayload(SeededMessage seededMessage) {
        return new MessagePart()
                .setMimeType("text/plain")
                .setHeaders(
                        List.of(
                                header("From", seededMessage.from()),
                                header("Subject", seededMessage.subject()),
                                header(
                                        "Message-ID",
                                        "<" + seededMessage.messageId() + "@e2e-stub.invalid>"),
                                header("To", "e2e-stub-user-1@e2e-stub.invalid")))
                .setBody(new MessagePartBody().setData(base64Url(seededMessage.body())));
    }

    private void applyLabelMutation(String messageId, ModifyMessageRequest request) {
        LinkedHashSet<String> labelIds =
                seededMessageLabelIds.computeIfAbsent(
                        messageId, _ -> new LinkedHashSet<>(List.of(INBOX_LABEL_ID)));
        if (request.getAddLabelIds() != null) {
            labelIds.addAll(request.getAddLabelIds());
        }
        if (request.getRemoveLabelIds() != null) {
            labelIds.removeAll(request.getRemoveLabelIds());
        }
    }

    private Draft createDraft(Draft draft) {
        String draftId = "draft-" + draftSequence.incrementAndGet();
        Message draftMessage = draft.getMessage() == null ? new Message() : draft.getMessage();
        String threadId = draftMessage.getThreadId();
        String messageId = findMessageIdByThreadId(threadId);
        String draftBody = decodeRawMime(draftMessage.getRaw());
        SeededDraft seededDraft = new SeededDraft(draftId, messageId, draftBody);
        seededDrafts.put(draftId, seededDraft);
        return new Draft()
                .setId(draftId)
                .setMessage(
                        new Message()
                                .setId(messageId)
                                .setThreadId(threadId)
                                .setRaw(draftMessage.getRaw()));
    }

    private Draft draftById(String draftId) {
        SeededDraft seededDraft = seededDrafts.get(draftId);
        if (seededDraft == null) {
            return null;
        }
        SeededMessage seededMessage = seededMessages.get(seededDraft.messageId());
        String threadId =
                seededMessage == null ? seededDraft.messageId() : seededMessage.threadId();
        return new Draft()
                .setId(seededDraft.draftId())
                .setMessage(
                        new Message()
                                .setId(seededDraft.messageId())
                                .setThreadId(threadId)
                                .setRaw(base64Url(seededDraft.body())));
    }

    private String findMessageIdByThreadId(String threadId) {
        return seededMessages.values().stream()
                .filter(seededMessage -> Objects.equals(threadId, seededMessage.threadId()))
                .map(SeededMessage::messageId)
                .findFirst()
                .orElse(Objects.requireNonNullElse(threadId, "draft-message"));
    }

    private ListMessagesResponse listMessages(Gmail.Users.Messages.List request) {
        List<Message> messages =
                seededMessages.values().stream()
                        .filter(seededMessage -> matchesMessageListRequest(seededMessage, request))
                        .map(
                                seededMessage ->
                                        new Message()
                                                .setId(seededMessage.messageId())
                                                .setThreadId(seededMessage.threadId()))
                        .toList();
        Long maxResults = request.getMaxResults();
        if (maxResults != null && maxResults < messages.size()) {
            messages = messages.subList(0, Math.toIntExact(maxResults));
        }
        return new ListMessagesResponse().setMessages(messages);
    }

    private boolean matchesMessageListRequest(
            SeededMessage seededMessage, Gmail.Users.Messages.List request) {
        List<String> requestedLabelIds = request.getLabelIds();
        List<String> messageLabelIds = labelsForMessage(seededMessage.messageId());
        if (requestedLabelIds != null && !messageLabelIds.containsAll(requestedLabelIds)) {
            return false;
        }
        String query = request.getQ();
        if (query != null
                && query.contains("in:sent")
                && !messageLabelIds.contains(SENT_LABEL_ID)) {
            return false;
        }
        return true;
    }

    private ListHistoryResponse listHistory() {
        List<HistoryMessageAdded> messagesAdded =
                seededMessages.values().stream()
                        .map(
                                seededMessage ->
                                        new HistoryMessageAdded()
                                                .setMessage(
                                                        new Message()
                                                                .setId(seededMessage.messageId())
                                                                .setThreadId(
                                                                        seededMessage.threadId())))
                        .toList();
        History history =
                new History()
                        .setId(BigInteger.valueOf(System.currentTimeMillis()))
                        .setMessagesAdded(messagesAdded);
        return new ListHistoryResponse().setHistory(List.of(history));
    }

    private com.google.api.services.gmail.model.Thread threadById(String threadId) {
        List<Message> messages =
                seededMessages.values().stream()
                        .filter(seededMessage -> Objects.equals(threadId, seededMessage.threadId()))
                        .map(seededMessage -> toGmailMessage(seededMessage.messageId()))
                        .toList();
        return new com.google.api.services.gmail.model.Thread()
                .setId(threadId)
                .setMessages(messages);
    }

    private Label createLabel(Label requestedLabel) {
        String labelName = requestedLabel.getName();
        String labelId =
                "Label_"
                        + labelName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                        + "_"
                        + labelSequence.incrementAndGet();
        Label label = label(labelId, labelName);
        seededLabels.put(labelId, label);
        return label;
    }

    private static Label label(String labelId, String labelName) {
        return new Label().setId(labelId).setName(labelName);
    }

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader()
                .setName(name)
                .setValue(Objects.requireNonNullElse(value, ""));
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeRawMime(String rawMime) {
        if (rawMime == null || rawMime.isBlank()) {
            return "";
        }
        String paddedRawMime = rawMime + "=".repeat((4 - rawMime.length() % 4) % 4);
        byte[] decodedMime = Base64.getUrlDecoder().decode(paddedRawMime);
        return new String(decodedMime, StandardCharsets.UTF_8);
    }

    private static HttpTransport failLoudTransport() {
        return new HttpTransport() {
            @Override
            protected LowLevelHttpRequest buildRequest(String method, String url)
                    throws IOException {
                throw new IOException("e2e-stub: outbound Gmail HTTP forbidden");
            }
        };
    }

    private static HttpRequestInitializer noopRequestInitializer() {
        return _ -> {};
    }

    private static final class OfflineGmail extends Gmail {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineGmail(E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            super(failLoudTransport(), GsonFactory.getDefaultInstance(), noopRequestInitializer());
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Users users() {
            return new OfflineUsers(this, e2eStubGmailFactory);
        }
    }

    private static final class OfflineUsers extends Gmail.Users {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineUsers(OfflineGmail gmail, E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            gmail.super();
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Messages messages() {
            return new OfflineMessages(this, e2eStubGmailFactory);
        }

        @Override
        public Drafts drafts() {
            return new OfflineDrafts(this, e2eStubGmailFactory);
        }

        @Override
        public Labels labels() {
            return new OfflineLabels(this, e2eStubGmailFactory);
        }

        @Override
        public History history() {
            return new OfflineHistory(this, e2eStubGmailFactory);
        }

        @Override
        public Threads threads() {
            return new OfflineThreads(this, e2eStubGmailFactory);
        }

        @Override
        public Watch watch(String userId, WatchRequest content) throws IOException {
            return new OfflineWatch(this);
        }

        @Override
        public Stop stop(String userId) throws IOException {
            return new OfflineStop(this);
        }
    }

    private static final class OfflineMessages extends Gmail.Users.Messages {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineMessages(
                Gmail.Users users, E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            users.super();
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Get get(String userId, String messageId) throws IOException {
            return new OfflineMessageGet(this, messageId, e2eStubGmailFactory);
        }

        @Override
        public Gmail.Users.Messages.List list(String userId) throws IOException {
            return new OfflineMessageList(this, e2eStubGmailFactory);
        }

        @Override
        public Modify modify(String userId, String messageId, ModifyMessageRequest content)
                throws IOException {
            return new OfflineMessageModify(this, messageId, content, e2eStubGmailFactory);
        }
    }

    private static final class OfflineMessageGet extends Gmail.Users.Messages.Get {

        private final String messageId;
        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineMessageGet(
                Gmail.Users.Messages messages,
                String messageId,
                E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            messages.super(USER_ID, messageId);
            this.messageId = messageId;
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Message execute() {
            return e2eStubGmailFactory.toGmailMessage(messageId);
        }
    }

    private static final class OfflineMessageList extends Gmail.Users.Messages.List {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineMessageList(
                Gmail.Users.Messages messages, E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            messages.super(USER_ID);
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public ListMessagesResponse execute() {
            return e2eStubGmailFactory.listMessages(this);
        }
    }

    private static final class OfflineMessageModify extends Gmail.Users.Messages.Modify {

        private final String messageId;
        private final ModifyMessageRequest request;
        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineMessageModify(
                Gmail.Users.Messages messages,
                String messageId,
                ModifyMessageRequest request,
                E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            messages.super(USER_ID, messageId, request);
            this.messageId = messageId;
            this.request = request;
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Message execute() {
            e2eStubGmailFactory.applyLabelMutation(messageId, request);
            return e2eStubGmailFactory.toGmailMessage(messageId);
        }
    }

    private static final class OfflineDrafts extends Gmail.Users.Drafts {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineDrafts(Gmail.Users users, E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            users.super();
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Create create(String userId, Draft content) throws IOException {
            return new OfflineDraftCreate(this, content, e2eStubGmailFactory);
        }

        @Override
        public Get get(String userId, String draftId) throws IOException {
            return new OfflineDraftGet(this, draftId, e2eStubGmailFactory);
        }

        @Override
        public Delete delete(String userId, String draftId) throws IOException {
            return new OfflineDraftDelete(this, draftId, e2eStubGmailFactory);
        }
    }

    private static final class OfflineDraftCreate extends Gmail.Users.Drafts.Create {

        private final Draft draft;
        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineDraftCreate(
                Gmail.Users.Drafts drafts,
                Draft draft,
                E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            drafts.super(USER_ID, draft);
            this.draft = draft;
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Draft execute() {
            return e2eStubGmailFactory.createDraft(draft);
        }
    }

    private static final class OfflineDraftGet extends Gmail.Users.Drafts.Get {

        private final String draftId;
        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineDraftGet(
                Gmail.Users.Drafts drafts,
                String draftId,
                E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            drafts.super(USER_ID, draftId);
            this.draftId = draftId;
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Draft execute() {
            return e2eStubGmailFactory.draftById(draftId);
        }
    }

    private static final class OfflineDraftDelete extends Gmail.Users.Drafts.Delete {

        private final String draftId;
        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineDraftDelete(
                Gmail.Users.Drafts drafts,
                String draftId,
                E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            drafts.super(USER_ID, draftId);
            this.draftId = draftId;
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Void execute() {
            e2eStubGmailFactory.seededDrafts.remove(draftId);
            return null;
        }
    }

    private static final class OfflineLabels extends Gmail.Users.Labels {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineLabels(Gmail.Users users, E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            users.super();
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Gmail.Users.Labels.List list(String userId) throws IOException {
            return new OfflineLabelList(this, e2eStubGmailFactory);
        }

        @Override
        public Create create(String userId, Label content) throws IOException {
            return new OfflineLabelCreate(this, content, e2eStubGmailFactory);
        }
    }

    private static final class OfflineLabelList extends Gmail.Users.Labels.List {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineLabelList(
                Gmail.Users.Labels labels, E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            labels.super(USER_ID);
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public ListLabelsResponse execute() {
            return new ListLabelsResponse()
                    .setLabels(new ArrayList<>(e2eStubGmailFactory.seededLabels.values()));
        }
    }

    private static final class OfflineLabelCreate extends Gmail.Users.Labels.Create {

        private final Label requestedLabel;
        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineLabelCreate(
                Gmail.Users.Labels labels,
                Label requestedLabel,
                E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            labels.super(USER_ID, requestedLabel);
            this.requestedLabel = requestedLabel;
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Label execute() {
            return e2eStubGmailFactory.createLabel(requestedLabel);
        }
    }

    private static final class OfflineHistory extends Gmail.Users.History {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineHistory(
                Gmail.Users users, E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            users.super();
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Gmail.Users.History.List list(String userId) throws IOException {
            return new OfflineHistoryList(this, e2eStubGmailFactory);
        }
    }

    private static final class OfflineHistoryList extends Gmail.Users.History.List {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineHistoryList(
                Gmail.Users.History history, E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            history.super(USER_ID);
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public ListHistoryResponse execute() {
            return e2eStubGmailFactory.listHistory();
        }
    }

    private static final class OfflineThreads extends Gmail.Users.Threads {

        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineThreads(
                Gmail.Users users, E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            users.super();
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public Get get(String userId, String threadId) throws IOException {
            return new OfflineThreadGet(this, threadId, e2eStubGmailFactory);
        }
    }

    private static final class OfflineThreadGet extends Gmail.Users.Threads.Get {

        private final String threadId;
        private final E2eStubGmailApiClientFactory e2eStubGmailFactory;

        private OfflineThreadGet(
                Gmail.Users.Threads threads,
                String threadId,
                E2eStubGmailApiClientFactory e2eStubGmailFactory) {
            threads.super(USER_ID, threadId);
            this.threadId = threadId;
            this.e2eStubGmailFactory = e2eStubGmailFactory;
        }

        @Override
        public com.google.api.services.gmail.model.Thread execute() {
            return e2eStubGmailFactory.threadById(threadId);
        }
    }

    private static final class OfflineWatch extends Gmail.Users.Watch {

        private OfflineWatch(Gmail.Users users) {
            users.super(USER_ID, new WatchRequest());
        }

        @Override
        public WatchResponse execute() {
            return new WatchResponse()
                    .setHistoryId(BigInteger.valueOf(System.currentTimeMillis()))
                    .setExpiration(Instant.now().plus(Duration.ofDays(7)).toEpochMilli());
        }
    }

    private static final class OfflineStop extends Gmail.Users.Stop {

        private OfflineStop(Gmail.Users users) {
            users.super(USER_ID);
        }

        @Override
        public Void execute() {
            return null;
        }
    }
}
