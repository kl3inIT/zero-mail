package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.llm.exception.LlmEvaluationFailedException;
import com.zeromail.core.llm.exception.TokenBudgetExceededException;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.shared.privacy.SensitiveMarkerScrubFilter;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import com.zeromail.core.triage.usecases.TriageOrchestratorService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TriagePrivacySweepTest.MeterRegistryTestConfiguration.class)
@SuppressWarnings("SqlResolve")
class TriagePrivacySweepTest extends PostgresContainerTest {

    private static final UUID GMAIL_CONNECTION_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000004a8");
    private static final String GMAIL_MESSAGE_ID = "gmail-message-privacy-sweep";
    private static final String GMAIL_THREAD_ID = "gmail-thread-privacy-sweep";
    private static final String RAW_SENDER_EMAIL = "sweep.sender@example.test";
    private static final String SENDER_DISPLAY_NAME_SENTINEL =
            "EMAIL_SENDER_DISPLAY_NAME_SENTINEL_04_08";
    private static final String EMAIL_SUBJECT_SENTINEL = "EMAIL_SUBJECT_SENTINEL_04_08";
    private static final String EMAIL_SNIPPET_SENTINEL = "EMAIL_SNIPPET_SENTINEL_04_08";
    private static final String EMAIL_BODY_SENTINEL = "EMAIL_BODY_SENTINEL_04_08";
    private static final String EMAIL_DRAFT_BODY_SENTINEL = "EMAIL_DRAFT_BODY_SENTINEL_04_08";
    private static final String LLM_PROMPT_SENTINEL = "LLM_PROMPT_SENTINEL_04_08";
    private static final String LLM_COMPLETION_SENTINEL = "LLM_COMPLETION_SENTINEL_04_08";
    private static final String RULE_AUTHORED_STATIC_INSTRUCTION =
            "RULE_AUTHORED_STATIC_INSTRUCTION_04_08";
    private static final List<String> FORBIDDEN_CONTENT_TOKENS =
            List.of(
                    SENDER_DISPLAY_NAME_SENTINEL,
                    EMAIL_SUBJECT_SENTINEL,
                    EMAIL_SNIPPET_SENTINEL,
                    EMAIL_BODY_SENTINEL,
                    EMAIL_DRAFT_BODY_SENTINEL,
                    LLM_PROMPT_SENTINEL,
                    LLM_COMPLETION_SENTINEL,
                    RAW_SENDER_EMAIL);

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MeterRegistry meterRegistry;
    @Autowired RuleRepository ruleRepository;
    @Autowired TriageOrchestratorService triageOrchestratorService;

    @MockitoBean GmailApiClientFactory gmailApiClientFactory;
    @MockitoBean LlmGateway llmGateway;
    @MockitoBean TriageGmailWriter triageGmailWriter;

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogCapture() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.addFilter(new SensitiveMarkerScrubFilter());
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogCapture() {
        rootLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void triage_audit_logs_and_metric_tags_never_expose_message_content_or_raw_sender()
            throws Exception {
        UUID tenantId = seedTenant();
        UUID deterministicRuleId = UUID.randomUUID();
        UUID semanticRuleId = UUID.randomUUID();
        seedOptedInSender(tenantId);
        seedRules(tenantId, deterministicRuleId, semanticRuleId);
        configureGmailMetadataResponse(tenantId);
        configureOpaqueSemanticFailure();

        withTenant(
                tenantId,
                () ->
                        triageOrchestratorService.processObservedEvent(
                                new MailMessageObserved(
                                        tenantId,
                                        GMAIL_CONNECTION_ID,
                                        GMAIL_MESSAGE_ID,
                                        GMAIL_THREAD_ID,
                                        Instant.parse("2026-05-11T00:00:00Z"))));

        verify(triageGmailWriter).archiveSkipInbox(tenantId, GMAIL_MESSAGE_ID);
        assertAuditRowsAreContentFree(tenantId);
        assertCapturedTriageLogsAreContentFree();
        assertTriageMetricTagsAreContentFree(tenantId);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        String displayName = "triage-privacy-sweep-" + tenantId;
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
        jdbcTemplate.update(
                "insert into gmail_connections(id, tenant_id, google_email, status, is_primary) values (?, ?, ?, 'CONNECTED', true)",
                GMAIL_CONNECTION_ID,
                tenantId,
                displayName + "@example.test");
        return tenantId;
    }

    private void seedOptedInSender(UUID tenantId) {
        jdbcTemplate.update(
                """
        insert into tenant_sender_opt_in(id, tenant_id, sender_email)
        values (?, ?, ?)
        """,
                UUID.randomUUID(),
                tenantId,
                RAW_SENDER_EMAIL);
    }

    private void seedRules(UUID tenantId, UUID deterministicRuleId, UUID semanticRuleId) {
        withTenant(
                tenantId,
                () -> {
                    RuleEntity deterministicRule =
                            new RuleEntity(
                                    deterministicRuleId,
                                    tenantId,
                                    GMAIL_CONNECTION_ID,
                                    "Archive opted-in sender",
                                    "Archive opted-in sender",
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
                    deterministicRule.setEnabled(true);

                    RuleEntity semanticRule =
                            new RuleEntity(
                                    semanticRuleId,
                                    tenantId,
                                    GMAIL_CONNECTION_ID,
                                    "Semantic draft probe",
                                    "Semantic draft probe",
                                    RuleLanguage.EN,
                                    RuleSchemaVersion.RULES_V1,
                                    """
                  {"schemaVersion":"rules.v1","type":"SEMANTIC_INTENT","nodeId":"semantic-node","intent":"%s","deferred":true}
                  """
                                            .formatted(LLM_PROMPT_SENTINEL),
                                    """
                  [{"type":"save_draft","instruction":"%s"}]
                  """
                                            .formatted(RULE_AUTHORED_STATIC_INSTRUCTION),
                                    20,
                                    null,
                                    null);
                    semanticRule.setEnabled(true);

                    ruleRepository.saveAndFlush(deterministicRule);
                    ruleRepository.saveAndFlush(semanticRule);
                });
    }

    private void configureGmailMetadataResponse(UUID tenantId) throws Exception {
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Messages messages = mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.Get messageGetRequest = mock(Gmail.Users.Messages.Get.class);

        when(gmailApiClientFactory.buildClientForTenant(tenantId)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.get("me", GMAIL_MESSAGE_ID)).thenReturn(messageGetRequest);
        when(messageGetRequest.setFormat("metadata")).thenReturn(messageGetRequest);
        when(messageGetRequest.setFields(anyString())).thenReturn(messageGetRequest);
        when(messageGetRequest.setMetadataHeaders(anyList())).thenReturn(messageGetRequest);
        when(messageGetRequest.execute()).thenReturn(gmailMetadataMessage());
    }

    private void configureOpaqueSemanticFailure() {
        when(llmGateway.evaluateSemanticIntents(
                        eq(CallSite.TRIAGE_PLATFORM_LLM), anyString(), anyList()))
                .thenThrow(new TokenBudgetExceededException(5000, 3896))
                .thenThrow(
                        new LlmEvaluationFailedException(
                                new IllegalStateException(LLM_COMPLETION_SENTINEL)));
    }

    private static Message gmailMetadataMessage() {
        MessagePart payload =
                new MessagePart()
                        .setHeaders(
                                List.of(
                                        new MessagePartHeader()
                                                .setName("From")
                                                .setValue(
                                                        SENDER_DISPLAY_NAME_SENTINEL
                                                                + " <"
                                                                + RAW_SENDER_EMAIL
                                                                + ">"),
                                        new MessagePartHeader()
                                                .setName("To")
                                                .setValue("founder@example.test"),
                                        new MessagePartHeader()
                                                .setName("Subject")
                                                .setValue(EMAIL_SUBJECT_SENTINEL)));
        return new Message()
                .setId(GMAIL_MESSAGE_ID)
                .setThreadId(GMAIL_THREAD_ID)
                .setLabelIds(List.of("INBOX", "CATEGORY_PERSONAL"))
                .setInternalDate(1_776_384_000_000L)
                .setSnippet(EMAIL_SNIPPET_SENTINEL + " " + EMAIL_BODY_SENTINEL)
                .setPayload(payload);
    }

    private void assertAuditRowsAreContentFree(UUID tenantId) {
        List<String> auditSurfaces =
                jdbcTemplate.queryForList(
                        """
            select coalesce(reason, '')
                || E'\\n' || coalesce(action_args_json::text, '')
                || E'\\n' || coalesce(gmail_change_token::text, '')
                || E'\\n' || coalesce(external_ref, '')
                || E'\\n' || coalesce(failure_reason, '') as audit_surface
            from triage_audit
            where tenant_id = ? and gmail_message_id = ?
            order by decided_at
            """,
                        String.class,
                        tenantId,
                        GMAIL_MESSAGE_ID);
        assertThat(auditSurfaces)
                .as(
                        "the synthetic triage run should create an audit row for the deterministic archive")
                .hasSize(1);
        assertNoForbiddenContent("triage_audit fields", String.join("\n", auditSurfaces));
    }

    private void assertCapturedTriageLogsAreContentFree() {
        List<String> capturedTriageLines =
                logAppender.list.stream()
                        .map(
                                loggingEvent ->
                                        loggingEvent.getFormattedMessage()
                                                + " "
                                                + loggingEvent.getMDCPropertyMap())
                        .filter(capturedLine -> capturedLine.contains("triage"))
                        .toList();

        assertThat(capturedTriageLines)
                .as("the sweep should capture the sender safety-net check log line")
                .anySatisfy(
                        capturedLine ->
                                assertThat(capturedLine)
                                        .contains("event=triage_sender_safety_net_checked")
                                        .contains("senderEmailHash="));
        assertNoForbiddenContent("triage log lines", String.join("\n", capturedTriageLines));
    }

    private void assertTriageMetricTagsAreContentFree(UUID tenantId) {
        assertThat(meterRegistry.find("triage.semantic_eval.fanout.per_rule").counter())
                .as("the semantic fanout counter should be emitted by the opaque LLM failure path")
                .isNotNull();

        List<String> triageMetricTags =
                meterRegistry.getMeters().stream()
                        .filter(meter -> meter.getId().getName().startsWith("triage."))
                        .flatMap(meter -> meter.getId().getTags().stream())
                        .map(tag -> tag.getKey() + "=" + tag.getValue())
                        .toList();
        String tagSurface = String.join("\n", triageMetricTags);
        assertNoForbiddenContent("triage Micrometer tags", tagSurface);
        assertThat(tagSurface)
                .as("triage metric tags must not expose the raw tenant id")
                .doesNotContain(tenantId.toString());
    }

    private static void assertNoForbiddenContent(String surfaceName, String surface) {
        for (String forbiddenContentToken : FORBIDDEN_CONTENT_TOKENS) {
            assertThat(surface)
                    .as("%s leaked forbidden token: %s", surfaceName, forbiddenContentToken)
                    .doesNotContain(forbiddenContentToken);
        }
    }

    private static void withTenant(UUID tenantId, TenantRunnable tenantRunnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(tenantRunnable::run);
    }

    @FunctionalInterface
    private interface TenantRunnable {
        void run();
    }

    @TestConfiguration
    static class MeterRegistryTestConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
