package com.zeromail.core.chat.usecases.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.chat.domain.ChatId;
import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.persistence.AssistantKnowledgeMemoryEntity;
import com.zeromail.core.chat.persistence.AssistantKnowledgeMemoryJpaRepository;
import com.zeromail.core.chat.persistence.ChatEntity;
import com.zeromail.core.chat.persistence.ChatJpaRepository;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.chat.sanitize.ToolOutputSanitizer;
import com.zeromail.core.chat.usecases.ChatStreamSink;
import com.zeromail.core.chat.usecases.SanitizingSink;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@SuppressWarnings({"SqlResolve", "unchecked"})
class ReadToolsIT extends PostgresContainerTest {

    private static final String SEARCH_MESSAGE_ID = "search-message-07-03";
    private static final String GET_MESSAGE_ID = "get-message-07-03";
    private static final String THREAD_ID = "thread-07-03";
    private static final String BODY_SENTINEL = "EMAIL_BODY_SENTINEL_07_03";

    @Autowired SearchInboxToolHandler searchInboxToolHandler;
    @Autowired GetMessageToolHandler getMessageToolHandler;
    @Autowired ListLabelsToolHandler listLabelsToolHandler;
    @Autowired GetThreadToolHandler getThreadToolHandler;
    @Autowired ListRulesToolHandler listRulesToolHandler;
    @Autowired GetRuleToolHandler getRuleToolHandler;
    @Autowired GetSenderSafetyEntryToolHandler getSenderSafetyEntryToolHandler;
    @Autowired SearchMemoriesToolHandler searchMemoriesToolHandler;
    @Autowired RuleRepository ruleRepository;
    @Autowired AssistantKnowledgeMemoryJpaRepository assistantKnowledgeMemoryRepository;
    @Autowired ChatJpaRepository chatRepository;
    @Autowired ChatMessageJdbcRepository chatMessageJdbcRepository;
    @Autowired ToolOutputSanitizer toolOutputSanitizer;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean GmailApiClientFactory gmailApiClientFactory;

    @Test
    void read_tools_return_tenant_scoped_json_and_sanitize_get_message_body() throws Exception {
        UUID tenantAId = seedTenant("tenant-a");
        UUID tenantBId = seedTenant("tenant-b");
        UUID ruleId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        seedRule(tenantAId, ruleId);
        seedMemories(tenantAId, tenantBId);
        seedProtectedSender(tenantAId);
        withTenant(
                tenantAId,
                () -> chatRepository.saveAndFlush(new ChatEntity(chatId, tenantAId, "Read tools")));
        configureGmail(tenantAId);

        withTenant(
                tenantAId,
                () -> {
                    String tenantId = tenantAId.toString();

                    assertThat(
                                    searchInboxToolHandler.executeJson(
                                            "{\"query\":\"from:vip@example.test\",\"maxResults\":5}",
                                            tenantId))
                            .contains(SEARCH_MESSAGE_ID)
                            .contains("Quarterly update")
                            .doesNotContain(BODY_SENTINEL);

                    String messageJson =
                            getMessageToolHandler.executeJson(
                                    "{\"messageId\":\"" + GET_MESSAGE_ID + "\"}", tenantId);
                    assertThat(messageJson).contains("\"bodyText\"").contains(BODY_SENTINEL);

                    assertThat(listLabelsToolHandler.executeJson("{}", tenantId))
                            .contains("Label_123")
                            .contains("Project");

                    assertThat(
                                    getThreadToolHandler.executeJson(
                                            "{\"threadId\":\"" + THREAD_ID + "\"}", tenantId))
                            .contains(THREAD_ID)
                            .contains("founder@example.test")
                            .contains(SEARCH_MESSAGE_ID);

                    assertThat(listRulesToolHandler.executeJson("{}", tenantId))
                            .contains(ruleId.toString())
                            .contains("Archive VIP updates");

                    assertThat(
                                    getRuleToolHandler.executeJson(
                                            "{\"ruleId\":\"" + ruleId + "\"}", tenantId))
                            .contains(ruleId.toString())
                            .contains("sender-domain");

                    assertThat(
                                    getSenderSafetyEntryToolHandler.executeJson(
                                            "{\"senderEmail\":\"vip@example.test\"}", tenantId))
                            .contains("\"mode\":\"protected\"")
                            .contains("recipientEmailHash")
                            .doesNotContain("vip@example.test");

                    assertThat(
                                    searchMemoriesToolHandler.executeJson(
                                            "{\"query\":\"Acme\"}", tenantId))
                            .contains("Tenant A Acme context")
                            .doesNotContain("Tenant B Acme context");

                    persistSanitizedToolOutput(chatId, tenantAId, messageJson);
                });

        String persistedPartsJson =
                jdbcTemplate.queryForObject(
                        """
                        select parts::text
                        from chat_message
                        where chat_id = ? and tenant_id = ?
                        order by created_at desc
                        limit 1
                        """,
                        String.class,
                        chatId,
                        tenantAId);
        assertThat(persistedPartsJson)
                .doesNotContain(BODY_SENTINEL)
                .doesNotContain("bodyText")
                .doesNotContain("decodedTextBody");

        withTenant(
                tenantBId,
                () ->
                        assertThat(
                                        searchMemoriesToolHandler.executeJson(
                                                "{\"query\":\"Acme\"}", tenantBId.toString()))
                                .contains("Tenant B Acme context")
                                .doesNotContain("Tenant A Acme context"));
    }

    private UUID seedTenant(String displayNamePrefix) {
        UUID tenantId = UUID.randomUUID();
        UUID gmailConnectionId = UUID.randomUUID();
        String displayName = displayNamePrefix + "-" + tenantId;
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
        jdbcTemplate.update(
                "insert into gmail_connections(id, tenant_id, google_email, status, is_primary) values (?, ?, ?, 'CONNECTED', true)",
                gmailConnectionId,
                tenantId,
                displayName + "@example.test");
        return tenantId;
    }

    private UUID primaryGmailConnectionId(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "select id from gmail_connections where tenant_id = ? and is_primary = true",
                UUID.class,
                tenantId);
    }

    private void seedRule(UUID tenantId, UUID ruleId) {
        withTenant(
                tenantId,
                () -> {
                    RuleEntity rule =
                            new RuleEntity(
                                    ruleId,
                                    tenantId,
                                    primaryGmailConnectionId(tenantId),
                                    "Archive VIP updates",
                                    "Archive updates from VIP senders",
                                    RuleLanguage.EN,
                                    RuleSchemaVersion.RULES_V1,
                                    """
                                    {"schemaVersion":"rules.v1","type":"SENDER_DOMAIN","nodeId":"sender-domain","domain":"example.test"}
                                    """,
                                    """
                                    [{"type":"archive"}]
                                    """,
                                    10,
                                    null,
                                    null);
                    rule.setEnabled(true);
                    ruleRepository.saveAndFlush(rule);
                });
    }

    private void seedMemories(UUID tenantAId, UUID tenantBId) {
        withTenant(
                tenantAId,
                () ->
                        assistantKnowledgeMemoryRepository.saveAllAndFlush(
                                List.of(
                                        new AssistantKnowledgeMemoryEntity(
                                                UUID.randomUUID(),
                                                tenantAId,
                                                "Tenant A Acme",
                                                "Tenant A Acme context for quarterly vendor updates"),
                                        new AssistantKnowledgeMemoryEntity(
                                                UUID.randomUUID(),
                                                tenantAId,
                                                "Tenant A other",
                                                "Tenant A nonmatching context"))));
        withTenant(
                tenantBId,
                () ->
                        assistantKnowledgeMemoryRepository.saveAllAndFlush(
                                List.of(
                                        new AssistantKnowledgeMemoryEntity(
                                                UUID.randomUUID(),
                                                tenantBId,
                                                "Tenant B Acme",
                                                "Tenant B Acme context must not leak"),
                                        new AssistantKnowledgeMemoryEntity(
                                                UUID.randomUUID(),
                                                tenantBId,
                                                "Tenant B unrelated",
                                                "Tenant B unrelated memory"))));
    }

    private void seedProtectedSender(UUID tenantId) {
        Instant observedAt = Instant.parse("2026-05-18T00:00:00Z");
        jdbcTemplate.update(
                """
                insert into tenant_protected_sender_observation(
                  id, tenant_id, sender_email, first_observed_at, last_observed_at,
                  observation_count, created_at, updated_at, version
                )
                values (?, ?, ?, ?, ?, 3, ?, ?, 0)
                """,
                UUID.randomUUID(),
                tenantId,
                "vip@example.test",
                Timestamp.from(observedAt),
                Timestamp.from(observedAt),
                Timestamp.from(observedAt),
                Timestamp.from(observedAt));
    }

    private void configureGmail(UUID tenantId) throws Exception {
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Messages messages = mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.List messagesListRequest = mock(Gmail.Users.Messages.List.class);
        Gmail.Users.Messages.Get searchMessageGetRequest = mock(Gmail.Users.Messages.Get.class);
        Gmail.Users.Messages.Get getMessageRequest = mock(Gmail.Users.Messages.Get.class);
        Gmail.Users.Labels labels = mock(Gmail.Users.Labels.class);
        Gmail.Users.Labels.List labelsListRequest = mock(Gmail.Users.Labels.List.class);
        Gmail.Users.Threads threads = mock(Gmail.Users.Threads.class);
        Gmail.Users.Threads.Get threadGetRequest = mock(Gmail.Users.Threads.Get.class);

        when(gmailApiClientFactory.buildClientForTenant(tenantId)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(users.labels()).thenReturn(labels);
        when(users.threads()).thenReturn(threads);

        when(messages.list("me")).thenReturn(messagesListRequest);
        when(messagesListRequest.setQ(anyString())).thenReturn(messagesListRequest);
        when(messagesListRequest.setMaxResults(anyLong())).thenReturn(messagesListRequest);
        when(messagesListRequest.execute())
                .thenReturn(
                        new ListMessagesResponse()
                                .setMessages(
                                        List.of(
                                                new Message()
                                                        .setId(SEARCH_MESSAGE_ID)
                                                        .setThreadId(THREAD_ID))));

        when(messages.get("me", SEARCH_MESSAGE_ID)).thenReturn(searchMessageGetRequest);
        when(searchMessageGetRequest.setFormat(anyString())).thenReturn(searchMessageGetRequest);
        when(searchMessageGetRequest.setFields(anyString())).thenReturn(searchMessageGetRequest);
        when(searchMessageGetRequest.setMetadataHeaders(anyList()))
                .thenReturn(searchMessageGetRequest);
        when(searchMessageGetRequest.execute()).thenReturn(searchMessage());

        when(messages.get("me", GET_MESSAGE_ID)).thenReturn(getMessageRequest);
        when(getMessageRequest.setFormat(anyString())).thenReturn(getMessageRequest);
        when(getMessageRequest.setFields(anyString())).thenReturn(getMessageRequest);
        when(getMessageRequest.execute()).thenReturn(fullMessage());

        when(labels.list("me")).thenReturn(labelsListRequest);
        when(labelsListRequest.execute())
                .thenReturn(
                        new ListLabelsResponse()
                                .setLabels(
                                        List.of(
                                                new Label()
                                                        .setId("Label_123")
                                                        .setName("Project")
                                                        .setType("user"))));

        when(threads.get("me", THREAD_ID)).thenReturn(threadGetRequest);
        when(threadGetRequest.setFormat(anyString())).thenReturn(threadGetRequest);
        when(threadGetRequest.setFields(anyString())).thenReturn(threadGetRequest);
        when(threadGetRequest.setMetadataHeaders(anyList())).thenReturn(threadGetRequest);
        when(threadGetRequest.execute())
                .thenReturn(
                        new com.google.api.services.gmail.model.Thread()
                                .setId(THREAD_ID)
                                .setMessages(List.of(searchMessage(), fullMessage())));
    }

    private static Message searchMessage() {
        return new Message()
                .setId(SEARCH_MESSAGE_ID)
                .setThreadId(THREAD_ID)
                .setInternalDate(1_779_033_600_000L)
                .setSnippet("Short Gmail snippet")
                .setPayload(
                        new MessagePart()
                                .setHeaders(
                                        List.of(
                                                header("From", "VIP <vip@example.test>"),
                                                header("To", "founder@example.test"),
                                                header("Subject", "Quarterly update")))
                                .setParts(
                                        List.of(
                                                new MessagePart()
                                                        .setFilename("brief.pdf")
                                                        .setMimeType("application/pdf"))));
    }

    private static Message fullMessage() {
        return new Message()
                .setId(GET_MESSAGE_ID)
                .setThreadId(THREAD_ID)
                .setInternalDate(1_779_120_000_000L)
                .setPayload(
                        new MessagePart()
                                .setMimeType("multipart/alternative")
                                .setHeaders(
                                        List.of(
                                                header("From", "VIP <vip@example.test>"),
                                                header("To", "founder@example.test"),
                                                header("Cc", "ops@example.test"),
                                                header("Subject", "Body carrying update")))
                                .setParts(
                                        List.of(
                                                new MessagePart()
                                                        .setMimeType("text/plain")
                                                        .setBody(
                                                                new MessagePartBody()
                                                                        .setData(encodedBody())))));
    }

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }

    private static String encodedBody() {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        ("This body is visible to the LLM only. " + BODY_SENTINEL)
                                .getBytes(StandardCharsets.UTF_8));
    }

    private void persistSanitizedToolOutput(
            UUID chatId, UUID tenantId, String rawMessageOutputJson) {
        PersistingSink persistingSink =
                new PersistingSink(chatMessageJdbcRepository, objectMapper, chatId, tenantId);
        SanitizingSink sanitizingSink =
                new SanitizingSink(persistingSink, toolOutputSanitizer, chatId);
        sanitizingSink.emitToolInputAvailable("tool-call-read-body", "getMessage", "{}");
        sanitizingSink.emitToolOutputAvailable("tool-call-read-body", rawMessageOutputJson);
    }

    private static void withTenant(UUID tenantId, TenantRunnable tenantRunnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(tenantRunnable::run);
    }

    @FunctionalInterface
    private interface TenantRunnable {
        void run();
    }

    private static final class PersistingSink implements ChatStreamSink {

        private final ChatMessageJdbcRepository chatMessageJdbcRepository;
        private final ObjectMapper objectMapper;
        private final UUID chatId;
        private final UUID tenantId;
        private String toolName;

        private PersistingSink(
                ChatMessageJdbcRepository chatMessageJdbcRepository,
                ObjectMapper objectMapper,
                UUID chatId,
                UUID tenantId) {
            this.chatMessageJdbcRepository = chatMessageJdbcRepository;
            this.objectMapper = objectMapper;
            this.chatId = chatId;
            this.tenantId = tenantId;
        }

        @Override
        public void emitTextStart(String partId) {}

        @Override
        public void emitTextDelta(String partId, String tokenText) {}

        @Override
        public void emitTextEnd(String partId) {}

        @Override
        public void emitToolInputStart(String toolCallId, String toolName) {
            this.toolName = toolName;
        }

        @Override
        public void emitToolInputAvailable(String toolCallId, String toolName, String inputJson) {
            this.toolName = toolName;
        }

        @Override
        public void emitToolOutputAvailable(String toolCallId, String outputJson) {
            try {
                Map<String, Object> output = objectMapper.readValue(outputJson, Map.class);
                chatMessageJdbcRepository.insert(
                        new ChatMessage(
                                null,
                                new ChatId(chatId),
                                tenantId.toString(),
                                ChatRole.TOOL,
                                ChatMessageParts.v1(
                                        List.of(
                                                new ToolOutputPart(
                                                        "tool-output-" + toolCallId,
                                                        toolCallId,
                                                        toolName,
                                                        "output-available",
                                                        output,
                                                        false))),
                                Instant.now()));
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "failed to persist sanitized tool output", exception);
            }
        }

        @Override
        public void emitDataPersistence(UUID chatMessageId, String state) {}

        @Override
        public void emitFinish(String reason) {}

        @Override
        public void emitError(String code, String userFacingMessage) {}

        @Override
        public void emitHeartbeat() {}
    }
}
