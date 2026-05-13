package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.draft.usecases.DraftBodyGenerator;
import com.zeromail.core.draft.usecases.DraftReplySourceLoader;
import com.zeromail.core.draft.usecases.DraftReplySourceLoader.DraftReplySource;
import com.zeromail.core.draft.usecases.GenerateThreadDraftCommand;
import com.zeromail.core.draft.usecases.GenerateThreadDraftService;
import com.zeromail.core.shared.lock.RedisDistributedLock;
import com.zeromail.core.shared.lock.RedisDistributedLock.LockHandle;
import com.zeromail.core.thread.persistence.ThreadReplyStatusRepository;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import com.zeromail.core.triage.usecases.TriageActionResultJsonValidator;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class DraftPrivacyLogScrubTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000005b3");

    @Test
    void draft_generation_logs_never_include_mail_body_prompt_or_completion_content()
            throws Exception {
        RedisDistributedLock redisDistributedLock = mock(RedisDistributedLock.class);
        DraftReplySourceLoader draftReplySourceLoader = mock(DraftReplySourceLoader.class);
        DraftBodyGenerator draftBodyGenerator = mock(DraftBodyGenerator.class);
        TriageGmailWriter triageGmailWriter = mock(TriageGmailWriter.class);
        ThreadReplyStatusRepository threadReplyStatusRepository =
                mock(ThreadReplyStatusRepository.class);
        ClassifyThreadReplyStatusService classifyThreadReplyStatusService =
                mock(ClassifyThreadReplyStatusService.class);
        TriageAuditWriter triageAuditWriter = mock(TriageAuditWriter.class);
        TriageAuditRepository triageAuditRepository = mock(TriageAuditRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        LockHandle lockHandle = mock(LockHandle.class);
        when(redisDistributedLock.tryAcquire(anyString(), any()))
                .thenReturn(Optional.of(lockHandle));
        when(threadReplyStatusRepository.findByGmailThreadId("thread-1"))
                .thenReturn(Optional.empty());
        when(draftReplySourceLoader.load(TENANT_ID, "thread-1"))
                .thenReturn(
                        new DraftReplySource(
                                "message-1",
                                "thread-1",
                                ReplyHeaders.of(
                                        "<message-1@example.test>",
                                        "",
                                        "prompt-sentinel",
                                        "sender@example.test",
                                        "thread-1"),
                                "sent-mail-body-sentinel",
                                "prompt-sentinel",
                                false,
                                false,
                                false));
        when(draftBodyGenerator.generate(
                        TENANT_ID, "thread-1", "sent-mail-body-sentinel", "prompt-sentinel"))
                .thenReturn("draft-body-sentinel completion-sentinel");
        when(triageGmailWriter.saveDraft(
                        eq(TENANT_ID),
                        any(ReplyHeaders.class),
                        eq("draft-body-sentinel completion-sentinel"),
                        eq("thread-1")))
                .thenReturn("draft-1");
        when(triageAuditWriter.insertPending(
                        any(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        any(),
                        anyString()))
                .thenReturn(Optional.of(UUID.randomUUID()));
        when(triageAuditRepository.reclaimStalePending(any(UUID.class), eq(TENANT_ID), anyString()))
                .thenReturn(1);
        GenerateThreadDraftService service =
                new GenerateThreadDraftService(
                        redisDistributedLock,
                        draftReplySourceLoader,
                        draftBodyGenerator,
                        triageGmailWriter,
                        threadReplyStatusRepository,
                        classifyThreadReplyStatusService,
                        triageAuditWriter,
                        triageAuditRepository,
                        new TriageActionResultJsonValidator(),
                        eventPublisher,
                        immediateTransactions(),
                        Clock.fixed(Instant.parse("2026-05-12T10:00:00Z"), ZoneOffset.UTC));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger)
                        LoggerFactory.getLogger(GenerateThreadDraftService.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            service.generateOrRegenerate(new GenerateThreadDraftCommand(TENANT_ID, "thread-1"));
        } finally {
            logger.detachAppender(listAppender);
        }

        List<String> capturedLogLines =
                listAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(String.join("\n", capturedLogLines))
                .contains("event=draft_generated tenantId=")
                .doesNotContain("sent-mail-body-sentinel")
                .doesNotContain("draft-body-sentinel")
                .doesNotContain("prompt-sentinel")
                .doesNotContain("completion-sentinel");
    }

    private static TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
    }
}
