package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.draft.exception.DraftGenerationFailedException;
import com.zeromail.core.draft.usecases.DraftBodyGenerator;
import com.zeromail.core.draft.usecases.DraftReplySourceLoader;
import com.zeromail.core.draft.usecases.DraftReplySourceLoader.DraftReplySource;
import com.zeromail.core.draft.usecases.GenerateThreadDraftCommand;
import com.zeromail.core.draft.usecases.GenerateThreadDraftService;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.shared.lock.RedisDistributedLock;
import com.zeromail.core.shared.lock.RedisDistributedLock.LockHandle;
import com.zeromail.core.shared.privacy.SensitiveMarkerScrubFilter;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.persistence.ThreadReplyStatusRepository;
import com.zeromail.core.thread.projection.NeedsReplyInboxQueryService;
import com.zeromail.core.thread.projection.NeedsReplyPage;
import com.zeromail.core.thread.projection.NeedsReplyPageQuery;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import com.zeromail.core.triage.projection.AuditLogPage;
import com.zeromail.core.triage.projection.AuditLogPageQuery;
import com.zeromail.core.triage.projection.AuditLogQueryService;
import com.zeromail.core.triage.usecases.TriageActionResultJsonValidator;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("SqlResolve")
class DraftPrivacySweepTest extends PostgresContainerTest {

    private static final String GMAIL_MESSAGE_ID = "gmail-message-draft-privacy-sweep";
    private static final String GMAIL_THREAD_ID = "gmail-thread-draft-privacy-sweep";
    private static final String GENERATED_DRAFT_ID = "gmail-draft-draft-privacy-sweep";
    private static final String RAW_SENDER_EMAIL = "draft.sweep.sender@example.test";
    private static final String EMAIL_SUBJECT_SENTINEL = "EMAIL_SUBJECT_SENTINEL_05B_07";
    private static final String EMAIL_BODY_SENTINEL = "EMAIL_BODY_SENTINEL_05B_07";
    private static final String SENT_MAIL_TONE_CONTEXT_SENTINEL =
            "SENT_MAIL_TONE_CONTEXT_SENTINEL_05B_07";
    private static final String LLM_PROMPT_SENTINEL = "LLM_PROMPT_SENTINEL_05B_07";
    private static final String LLM_COMPLETION_SENTINEL = "LLM_COMPLETION_SENTINEL_05B_07";
    private static final String DRAFT_BODY_SENTINEL = "DRAFT_BODY_SENTINEL_05B_07";
    private static final String GOOGLE_SUBJECT_SENTINEL = "GOOGLE_SUBJECT_SENTINEL_05B_07";
    private static final String TOKEN_BYTES_SENTINEL = "TOKEN_BYTES_SENTINEL_05B_07";
    private static final List<String> FORBIDDEN_CONTENT_TOKENS =
            List.of(
                    EMAIL_SUBJECT_SENTINEL,
                    EMAIL_BODY_SENTINEL,
                    SENT_MAIL_TONE_CONTEXT_SENTINEL,
                    LLM_PROMPT_SENTINEL,
                    LLM_COMPLETION_SENTINEL,
                    DRAFT_BODY_SENTINEL,
                    GOOGLE_SUBJECT_SENTINEL,
                    TOKEN_BYTES_SENTINEL,
                    RAW_SENDER_EMAIL);

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TriageAuditWriter triageAuditWriter;
    @Autowired TriageAuditRepository triageAuditRepository;
    @Autowired ThreadReplyStatusRepository threadReplyStatusRepository;
    @Autowired ClassifyThreadReplyStatusService classifyThreadReplyStatusService;
    @Autowired AuditLogQueryService auditLogQueryService;
    @Autowired NeedsReplyInboxQueryService needsReplyInboxQueryService;
    @Autowired PlatformTransactionManager transactionManager;

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
    void draft_classify_and_list_success_paths_never_leak_content_to_logs_exceptions_or_storage()
            throws Exception {
        UUID tenantId = seedTenant();
        DraftScenario draftScenario = newDraftScenario();
        when(draftScenario.threadReplySourceLoader().load(tenantId, GMAIL_THREAD_ID))
                .thenReturn(sentinelReplySource(GMAIL_MESSAGE_ID, GMAIL_THREAD_ID));
        when(draftScenario
                        .draftBodyGenerator()
                        .generate(
                                tenantId,
                                GMAIL_THREAD_ID,
                                EMAIL_BODY_SENTINEL
                                        + " "
                                        + SENT_MAIL_TONE_CONTEXT_SENTINEL
                                        + " "
                                        + LLM_PROMPT_SENTINEL,
                                EMAIL_SUBJECT_SENTINEL))
                .thenReturn(DRAFT_BODY_SENTINEL + " " + LLM_COMPLETION_SENTINEL);
        when(draftScenario
                        .triageGmailWriter()
                        .saveDraft(
                                eq(tenantId),
                                any(ReplyHeaders.class),
                                eq(DRAFT_BODY_SENTINEL + " " + LLM_COMPLETION_SENTINEL),
                                eq(GMAIL_THREAD_ID)))
                .thenReturn(GENERATED_DRAFT_ID);

        draftScenario
                .service()
                .generateOrRegenerate(new GenerateThreadDraftCommand(tenantId, GMAIL_THREAD_ID));
        AuditLogPage auditLogPage =
                auditLogQueryService.page(
                        tenantId,
                        new AuditLogPageQuery(
                                10, null, RuleActionType.SAVE_DRAFT.id(), null, null));
        NeedsReplyPage needsReplyPage =
                needsReplyInboxQueryService.page(
                        tenantId,
                        new NeedsReplyPageQuery(ThreadReplyBucket.TO_REPLY, false, 10, null));

        verify(draftScenario.triageGmailWriter())
                .saveDraft(
                        eq(tenantId),
                        any(ReplyHeaders.class),
                        eq(DRAFT_BODY_SENTINEL + " " + LLM_COMPLETION_SENTINEL),
                        eq(GMAIL_THREAD_ID));
        assertThat(auditLogPage.items()).hasSize(1);
        assertThat(needsReplyPage.items()).hasSize(1);
        assertNoForbiddenContent("captured draft/classify logs", capturedLogSurface());
        assertNoForbiddenContent("audit projection result", auditLogPage.toString());
        assertNoForbiddenContent("needs-reply projection result", needsReplyPage.toString());
        assertDraftPersistenceIsContentFree(tenantId, GMAIL_MESSAGE_ID, GMAIL_THREAD_ID);
    }

    @Test
    void draft_failure_paths_never_leak_content_to_logs_exceptions_or_storage() throws Exception {
        UUID tenantId = seedTenant();
        DraftScenario safetyFailureScenario = newDraftScenario();
        when(safetyFailureScenario.threadReplySourceLoader().load(tenantId, GMAIL_THREAD_ID))
                .thenReturn(sentinelReplySource(GMAIL_MESSAGE_ID, GMAIL_THREAD_ID));
        when(safetyFailureScenario
                        .draftBodyGenerator()
                        .generate(
                                tenantId,
                                GMAIL_THREAD_ID,
                                EMAIL_BODY_SENTINEL
                                        + " "
                                        + SENT_MAIL_TONE_CONTEXT_SENTINEL
                                        + " "
                                        + LLM_PROMPT_SENTINEL,
                                EMAIL_SUBJECT_SENTINEL))
                .thenThrow(new SafetyViolationException());

        assertThatThrownBy(
                        () ->
                                safetyFailureScenario
                                        .service()
                                        .generateOrRegenerate(
                                                new GenerateThreadDraftCommand(
                                                        tenantId, GMAIL_THREAD_ID)))
                .isInstanceOf(SafetyViolationException.class)
                .satisfies(
                        thrownFailure ->
                                assertNoForbiddenContent(
                                        "safety failure exception chain",
                                        exceptionChainSurface(thrownFailure)));

        verify(safetyFailureScenario.triageGmailWriter(), never())
                .saveDraft(any(), any(), anyString(), anyString());
        assertDraftPersistenceHasNoRows(tenantId, GMAIL_MESSAGE_ID, GMAIL_THREAD_ID);

        String gmailFailureThreadId = GMAIL_THREAD_ID + "-gmail-failure";
        String gmailFailureMessageId = GMAIL_MESSAGE_ID + "-gmail-failure";
        DraftScenario gmailFailureScenario = newDraftScenario();
        when(gmailFailureScenario.threadReplySourceLoader().load(tenantId, gmailFailureThreadId))
                .thenThrow(new IOException("gmail metadata unavailable"));

        assertThatThrownBy(
                        () ->
                                gmailFailureScenario
                                        .service()
                                        .generateOrRegenerate(
                                                new GenerateThreadDraftCommand(
                                                        tenantId, gmailFailureThreadId)))
                .isInstanceOf(DraftGenerationFailedException.class)
                .satisfies(
                        thrownFailure ->
                                assertNoForbiddenContent(
                                        "gmail failure exception chain",
                                        exceptionChainSurface(thrownFailure)));

        verify(gmailFailureScenario.triageGmailWriter(), never())
                .saveDraft(any(), any(), anyString(), anyString());
        assertDraftPersistenceHasNoRows(tenantId, gmailFailureMessageId, gmailFailureThreadId);
        assertNoForbiddenContent("captured draft failure logs", capturedLogSurface());
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "draft-privacy-sweep-" + tenantId);
        return tenantId;
    }

    private DraftScenario newDraftScenario() {
        RedisDistributedLock redisDistributedLock = mock(RedisDistributedLock.class);
        DraftReplySourceLoader threadReplySourceLoader = mock(DraftReplySourceLoader.class);
        DraftBodyGenerator draftBodyGenerator = mock(DraftBodyGenerator.class);
        TriageGmailWriter triageGmailWriter = mock(TriageGmailWriter.class);
        LockHandle lockHandle = mock(LockHandle.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(redisDistributedLock.tryAcquire(anyString(), any()))
                .thenReturn(Optional.of(lockHandle));

        GenerateThreadDraftService service =
                new GenerateThreadDraftService(
                        redisDistributedLock,
                        threadReplySourceLoader,
                        draftBodyGenerator,
                        triageGmailWriter,
                        threadReplyStatusRepository,
                        classifyThreadReplyStatusService,
                        triageAuditWriter,
                        triageAuditRepository,
                        new TriageActionResultJsonValidator(),
                        eventPublisher,
                        new TransactionTemplate(transactionManager),
                        Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneOffset.UTC));
        return new DraftScenario(
                service,
                threadReplySourceLoader,
                draftBodyGenerator,
                triageGmailWriter,
                lockHandle);
    }

    private static DraftReplySource sentinelReplySource(
            String gmailMessageId, String gmailThreadId) {
        return new DraftReplySource(
                gmailMessageId,
                gmailThreadId,
                ReplyHeaders.of(
                        "<draft-privacy-sweep-message@example.test>",
                        "",
                        EMAIL_SUBJECT_SENTINEL + " " + GOOGLE_SUBJECT_SENTINEL,
                        RAW_SENDER_EMAIL,
                        gmailThreadId),
                EMAIL_BODY_SENTINEL
                        + " "
                        + SENT_MAIL_TONE_CONTEXT_SENTINEL
                        + " "
                        + LLM_PROMPT_SENTINEL,
                EMAIL_SUBJECT_SENTINEL,
                false,
                false,
                false);
    }

    private void assertDraftPersistenceIsContentFree(
            UUID tenantId, String gmailMessageId, String gmailThreadId) {
        String auditPersistenceSurface =
                String.join(
                        "\n",
                        jdbcTemplate.queryForList(
                                """
                                select coalesce(rule_name_snapshot, '')
                                    || E'\\n' || coalesce(action_type, '')
                                    || E'\\n' || coalesce(action_args_json::text, '')
                                    || E'\\n' || coalesce(gmail_change_token::text, '')
                                    || E'\\n' || coalesce(reason, '')
                                    || E'\\n' || coalesce(external_ref, '')
                                    || E'\\n' || coalesce(failure_reason, '') as audit_surface
                                from triage_audit
                                where tenant_id = ? and gmail_message_id = ?
                                """,
                                String.class,
                                tenantId,
                                gmailMessageId));
        String replyStatusPersistenceSurface =
                String.join(
                        "\n",
                        jdbcTemplate.queryForList(
                                """
                                select coalesce(gmail_thread_id, '')
                                    || E'\\n' || coalesce(bucket, '')
                                    || E'\\n' || coalesce(last_classified_message_id, '')
                                    || E'\\n' || coalesce(draft_id, '') as reply_status_surface
                                from thread_reply_status
                                where tenant_id = ? and gmail_thread_id = ?
                                """,
                                String.class,
                                tenantId,
                                gmailThreadId));
        assertThat(auditPersistenceSurface)
                .as("successful draft generation should create metadata-only audit rows")
                .isNotBlank();
        assertThat(replyStatusPersistenceSurface)
                .as("successful draft generation should create metadata-only reply status")
                .isNotBlank();
        assertNoForbiddenContent("triage_audit persistence fields", auditPersistenceSurface);
        assertNoForbiddenContent(
                "thread_reply_status persistence fields", replyStatusPersistenceSurface);
    }

    private void assertDraftPersistenceHasNoRows(
            UUID tenantId, String gmailMessageId, String gmailThreadId) {
        Integer auditRows =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from triage_audit
                        where tenant_id = ? and gmail_message_id = ?
                        """,
                        Integer.class,
                        tenantId,
                        gmailMessageId);
        Integer replyStatusRows =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from thread_reply_status
                        where tenant_id = ? and gmail_thread_id = ?
                        """,
                        Integer.class,
                        tenantId,
                        gmailThreadId);
        assertThat(auditRows).isZero();
        assertThat(replyStatusRows).isZero();
    }

    private String capturedLogSurface() {
        return String.join(
                "\n",
                logAppender.list.stream()
                        .map(
                                loggingEvent ->
                                        loggingEvent.getFormattedMessage()
                                                + " "
                                                + loggingEvent.getMDCPropertyMap())
                        .toList());
    }

    private static String exceptionChainSurface(Throwable throwable) {
        StringBuilder exceptionSurface = new StringBuilder();
        Throwable currentThrowable = throwable;
        while (currentThrowable != null) {
            exceptionSurface
                    .append(currentThrowable.getClass().getName())
                    .append(":")
                    .append(currentThrowable.getMessage())
                    .append('\n');
            currentThrowable = currentThrowable.getCause();
        }
        return exceptionSurface.toString();
    }

    private static void assertNoForbiddenContent(String surfaceName, String surface) {
        for (String forbiddenContentToken : FORBIDDEN_CONTENT_TOKENS) {
            assertThat(surface)
                    .as("%s leaked forbidden token: %s", surfaceName, forbiddenContentToken)
                    .doesNotContain(forbiddenContentToken);
        }
    }

    private record DraftScenario(
            GenerateThreadDraftService service,
            DraftReplySourceLoader threadReplySourceLoader,
            DraftBodyGenerator draftBodyGenerator,
            TriageGmailWriter triageGmailWriter,
            LockHandle lockHandle) {}
}
