package com.zeromail.core.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.chat.usecases.settings.VoiceGenerationResult;
import com.zeromail.core.chat.usecases.settings.VoiceGenerationService;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.llm.byok.ByokRateLimiter;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;
import com.zeromail.core.llm.usecases.LlmModelClient;
import com.zeromail.core.llm.usecases.LlmProviderChatExecutor;
import com.zeromail.core.llm.usecases.LlmUsage;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
        properties = {
            "zero-mail.billing.beta.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=12"
        })
class VoiceGenerationFromSentLeakTest extends PostgresContainerTest {

    private static final String BODY_SENTINEL = "LEAK_SENTINEL_AB12CD34_VOICE_BODY";
    private static final String QUOTED_SENTINEL = "LEAK_SENTINEL_QUOTED_INBOUND";
    private static final String COMPLETION_SENTINEL = "LEAK_SENTINEL_XY99ZZ_COMPLETION";

    @Autowired VoiceGenerationService voiceGenerationService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;

    @MockitoBean GmailApiClientFactory gmailApiClientFactory;
    @MockitoBean ByokRateLimiter byokRateLimiter;
    @MockitoBean LlmModelClient platformLlmModelClient;
    @MockitoBean SanitizationPipeline sanitizationPipeline;
    @MockitoBean LlmProviderChatExecutor providerChatExecutor;

    @Test
    void generatedVoiceStyleDoesNotPersistRawMailPromptOrCompletion() throws Exception {
        UUID tenantId = seedTenantWithCredits(10);
        configureGmail(tenantId);
        given(sanitizationPipeline.sanitizeStructuredJson(anyString()))
                .willAnswer(
                        invocation ->
                                new SanitizationContext(
                                        invocation.getArgument(0, String.class), 32, false, null));
        given(platformLlmModelClient.call(any(LlmChatRequest.class)))
                .willReturn(
                        new LlmChatResult(
                                List.of(),
                                new LlmUsage(32, 12, "stop"),
                                COMPLETION_SENTINEL + " concise style guide"));
        ArgumentCaptor<LlmChatRequest> requestCaptor =
                ArgumentCaptor.forClass(LlmChatRequest.class);
        int settingsRowsBefore = countRows("assistant_settings", tenantId);
        int knowledgeRowsBefore = countRows("assistant_knowledge_snippet", tenantId);
        ListAppender<ILoggingEvent> logAppender = attachRootLogAppender();

        VoiceGenerationResult result;
        try {
            result = underTenant(tenantId, () -> voiceGenerationService.generate(tenantId, 5));
        } finally {
            detachRootLogAppender(logAppender);
        }

        org.mockito.BDDMockito.then(platformLlmModelClient).should().call(requestCaptor.capture());
        String capturedPrompt = requestCaptor.getValue().userMessage();
        assertThat(capturedPrompt).contains(BODY_SENTINEL);
        assertThat(capturedPrompt).doesNotContain(QUOTED_SENTINEL);
        assertThat(result.generatedStyle()).contains(COMPLETION_SENTINEL);

        assertThat(countRows("assistant_settings", tenantId)).isEqualTo(settingsRowsBefore);
        assertThat(countRows("assistant_knowledge_snippet", tenantId))
                .isEqualTo(knowledgeRowsBefore);
        assertThat(countAuditRowsContaining(tenantId, BODY_SENTINEL)).isZero();
        assertThat(countAuditRowsContaining(tenantId, QUOTED_SENTINEL)).isZero();
        assertThat(countAuditRowsContaining(tenantId, COMPLETION_SENTINEL)).isZero();
        assertThat(countRows("llm_call_audit", tenantId)).isEqualTo(1);

        List<String> logMessages =
                logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(logMessages).noneMatch(message -> message.contains(BODY_SENTINEL));
        assertThat(logMessages).noneMatch(message -> message.contains(QUOTED_SENTINEL));
        assertThat(logMessages).noneMatch(message -> message.contains(COMPLETION_SENTINEL));
    }

    private UUID seedTenantWithCredits(int credits) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "voice-generation-" + tenantId);
        underTenant(
                tenantId,
                () -> {
                    creditLedgerEntryRepository.saveAndFlush(
                            CreditLedgerEntryEntity.topup(
                                    UUID.randomUUID(),
                                    tenantId,
                                    credits,
                                    "VOICE-SEED-" + tenantId));
                    return null;
                });
        return tenantId;
    }

    private void configureGmail(UUID tenantId) throws Exception {
        Gmail gmail = org.mockito.Mockito.mock(Gmail.class);
        Gmail.Users users = org.mockito.Mockito.mock(Gmail.Users.class);
        Gmail.Users.Messages messages = org.mockito.Mockito.mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.List listRequest =
                org.mockito.Mockito.mock(Gmail.Users.Messages.List.class);
        Gmail.Users.Messages.Get getRequest =
                org.mockito.Mockito.mock(Gmail.Users.Messages.Get.class);
        Message sentMessage = sentMessage();

        given(gmailApiClientFactory.buildClientForTenant(tenantId)).willReturn(gmail);
        given(gmail.users()).willReturn(users);
        given(users.messages()).willReturn(messages);
        given(messages.list("me")).willReturn(listRequest);
        given(listRequest.setQ(anyString())).willReturn(listRequest);
        given(listRequest.setMaxResults(org.mockito.ArgumentMatchers.anyLong()))
                .willReturn(listRequest);
        given(listRequest.execute())
                .willReturn(
                        new ListMessagesResponse()
                                .setMessages(List.of(new Message().setId("sent-1"))));
        given(messages.get("me", "sent-1")).willReturn(getRequest);
        given(getRequest.setFormat(anyString())).willReturn(getRequest);
        given(getRequest.execute()).willReturn(sentMessage);
    }

    private static Message sentMessage() {
        String sampleText =
                "Here is my concise user-authored update. "
                        + BODY_SENTINEL
                        + "\nOn Tue, VIP wrote:\n"
                        + QUOTED_SENTINEL;
        return new Message()
                .setId("sent-1")
                .setPayload(
                        new MessagePart()
                                .setMimeType("multipart/alternative")
                                .setHeaders(
                                        List.of(
                                                header("From", "founder@example.test"),
                                                header("To", "vip@example.test"),
                                                header("Subject", "Sent sample")))
                                .setParts(
                                        List.of(
                                                new MessagePart()
                                                        .setMimeType("text/plain")
                                                        .setBody(
                                                                new MessagePartBody()
                                                                        .setData(
                                                                                encoded(
                                                                                        sampleText))))));
    }

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }

    private static String encoded(String content) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    private int countRows(String tableName, UUID tenantId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "select count(*) from " + tableName + " where tenant_id = ?",
                        Integer.class,
                        tenantId);
        return count == null ? 0 : count;
    }

    private int countAuditRowsContaining(UUID tenantId, String sentinel) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                          from llm_call_audit
                         where tenant_id = ?
                           and concat_ws(' ', provider, feature, model_id, credential_source, call_site) like ?
                        """,
                        Integer.class,
                        tenantId,
                        "%" + sentinel + "%");
        return count == null ? 0 : count;
    }

    private static ListAppender<ILoggingEvent> attachRootLogAppender() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
        return logAppender;
    }

    private static void detachRootLogAppender(ListAppender<ILoggingEvent> logAppender) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(logAppender);
    }

    private static <T> T underTenant(UUID tenantId, TenantCallable<T> tenantCallable) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantCallable::call);
    }

    @FunctionalInterface
    private interface TenantCallable<T> {
        T call();
    }
}
