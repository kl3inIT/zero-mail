package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.draft.domain.DraftStatus;
import com.zeromail.core.draft.exception.DraftGenerationFailedException;
import com.zeromail.core.draft.exception.DraftGenerationInFlightException;
import com.zeromail.core.draft.exception.DraftGenerationUnavailableException;
import com.zeromail.core.draft.usecases.DraftBodyGenerator;
import com.zeromail.core.draft.usecases.DraftReplySourceLoader;
import com.zeromail.core.draft.usecases.DraftReplySourceLoader.DraftReplySource;
import com.zeromail.core.draft.usecases.GenerateThreadDraftCommand;
import com.zeromail.core.draft.usecases.GenerateThreadDraftResult;
import com.zeromail.core.draft.usecases.GenerateThreadDraftService;
import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.shared.lock.LockBackendUnavailableException;
import com.zeromail.core.shared.lock.RedisDistributedLock;
import com.zeromail.core.shared.lock.RedisDistributedLock.LockHandle;
import com.zeromail.core.thread.event.ThreadDraftSaved;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.thread.usecases.ThreadReplyClassificationInput;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import com.zeromail.core.triage.usecases.TriageActionResultJsonValidator;
import com.zeromail.core.triage.usecases.TriageDraftAuditService;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class GenerateThreadDraftServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000005b3");
    private static final UUID MAILBOX_ID = UUID.fromString("00000000-0000-0000-0000-0000000005b4");
    private static final MailboxRef MAILBOX_REF = new MailboxRef(TENANT_ID, MAILBOX_ID);
    private static final UUID AUDIT_ID = UUID.fromString("00000000-0000-0000-0000-0000000005a1");
    private static final String THREAD_ID = "thread-1";
    private static final String MESSAGE_ID = "message-1";
    private static final String NEW_DRAFT_ID = "draft-new";
    private static final String OLD_DRAFT_ID = "draft-old";

    private RedisDistributedLock redisDistributedLock;
    private DraftReplySourceLoader draftReplySourceLoader;
    private DraftBodyGenerator draftBodyGenerator;
    private TriageGmailWriter triageGmailWriter;
    private GmailConnectionService gmailConnectionService;
    private ClassifyThreadReplyStatusService classifyThreadReplyStatusService;
    private TriageAuditWriter triageAuditWriter;
    private TriageAuditRepository triageAuditRepository;
    private TriageDraftAuditService triageDraftAuditService;
    private ApplicationEventPublisher eventPublisher;
    private LockHandle lockHandle;
    private GenerateThreadDraftService service;

    @BeforeEach
    void setUp() {
        redisDistributedLock = mock(RedisDistributedLock.class);
        draftReplySourceLoader = mock(DraftReplySourceLoader.class);
        draftBodyGenerator = mock(DraftBodyGenerator.class);
        triageGmailWriter = mock(TriageGmailWriter.class);
        gmailConnectionService = mock(GmailConnectionService.class);
        classifyThreadReplyStatusService = mock(ClassifyThreadReplyStatusService.class);
        triageAuditWriter = mock(TriageAuditWriter.class);
        triageAuditRepository = mock(TriageAuditRepository.class);
        triageDraftAuditService =
                new TriageDraftAuditService(
                        triageAuditWriter,
                        triageAuditRepository,
                        new TriageActionResultJsonValidator());
        eventPublisher = mock(ApplicationEventPublisher.class);
        lockHandle = mock(LockHandle.class);
        when(triageAuditRepository.reclaimStalePending(eq(AUDIT_ID), eq(TENANT_ID), anyString()))
                .thenReturn(1);
        when(gmailConnectionService.primaryMailboxRef(TENANT_ID))
                .thenReturn(Optional.of(MAILBOX_REF));
        service =
                new GenerateThreadDraftService(
                        redisDistributedLock,
                        draftReplySourceLoader,
                        draftBodyGenerator,
                        triageGmailWriter,
                        gmailConnectionService,
                        classifyThreadReplyStatusService,
                        triageDraftAuditService,
                        eventPublisher,
                        immediateTransactions(),
                        Clock.fixed(Instant.parse("2026-05-12T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void service_generates_non_empty_body_and_persists_new_draft_state() throws Exception {
        arrangeLock();
        when(classifyThreadReplyStatusService.currentDraftId(THREAD_ID))
                .thenReturn(Optional.empty());
        when(draftReplySourceLoader.load(MAILBOX_REF, THREAD_ID)).thenReturn(source());
        when(draftBodyGenerator.generate(
                        TENANT_ID, MAILBOX_REF, THREAD_ID, "inbound body", "Inbound subject"))
                .thenReturn("generated draft body");
        when(triageGmailWriter.saveDraft(
                        eq(MAILBOX_REF),
                        any(ReplyHeaders.class),
                        eq("generated draft body"),
                        eq(THREAD_ID)))
                .thenReturn(NEW_DRAFT_ID);
        when(triageAuditWriter.insertPending(
                        eq(TENANT_ID),
                        eq(MAILBOX_ID),
                        eq(MAILBOX_ID),
                        eq(MESSAGE_ID),
                        eq(THREAD_ID),
                        any(),
                        any(),
                        any(UUID.class),
                        anyString(),
                        any(),
                        any(),
                        eq("on_demand_draft")))
                .thenReturn(Optional.of(AUDIT_ID));

        GenerateThreadDraftResult result =
                service.generateOrRegenerate(new GenerateThreadDraftCommand(TENANT_ID, THREAD_ID));

        assertThat(result.draftId()).isEqualTo(NEW_DRAFT_ID);
        assertThat(result.status()).isEqualTo(DraftStatus.GENERATED);
        assertThat(result.openInGmailUrl()).endsWith("/#all/" + THREAD_ID);
        assertThat(GenerateThreadDraftResult.class.getRecordComponents())
                .extracting(recordComponent -> recordComponent.getName())
                .doesNotContain("body");
        InOrder auditBeforeGmailOrder = inOrder(triageAuditWriter, triageGmailWriter);
        auditBeforeGmailOrder
                .verify(triageAuditWriter)
                .insertPending(
                        eq(TENANT_ID),
                        eq(MAILBOX_ID),
                        eq(MAILBOX_ID),
                        eq(MESSAGE_ID),
                        eq(THREAD_ID),
                        any(),
                        any(),
                        any(UUID.class),
                        anyString(),
                        any(),
                        any(),
                        eq("on_demand_draft"));
        auditBeforeGmailOrder
                .verify(triageGmailWriter)
                .saveDraft(
                        eq(MAILBOX_REF),
                        any(ReplyHeaders.class),
                        eq("generated draft body"),
                        eq(THREAD_ID));
        verify(triageAuditRepository)
                .markApplied(eq(AUDIT_ID), eq(TENANT_ID), eq(NEW_DRAFT_ID), eq(null), anyString());
        ArgumentCaptor<ThreadReplyClassificationInput> classificationInputCaptor =
                ArgumentCaptor.forClass(ThreadReplyClassificationInput.class);
        verify(classifyThreadReplyStatusService).classify(classificationInputCaptor.capture());
        assertThat(classificationInputCaptor.getValue().hasZeroMailDraft()).isTrue();
        assertThat(classificationInputCaptor.getValue().zeroMailDraftId()).isEqualTo(NEW_DRAFT_ID);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isEqualTo(
                        new ThreadDraftSaved(
                                TENANT_ID,
                                THREAD_ID,
                                NEW_DRAFT_ID,
                                Instant.parse("2026-05-12T10:00:00Z")));
        verify(lockHandle).release();
    }

    @Test
    void existing_pending_audit_without_lease_skips_gmail_write() throws Exception {
        arrangeLock();
        when(classifyThreadReplyStatusService.currentDraftId(THREAD_ID))
                .thenReturn(Optional.empty());
        when(draftReplySourceLoader.load(MAILBOX_REF, THREAD_ID)).thenReturn(source());
        when(draftBodyGenerator.generate(
                        TENANT_ID, MAILBOX_REF, THREAD_ID, "inbound body", "Inbound subject"))
                .thenReturn("generated draft body");
        when(triageAuditWriter.insertPending(
                        eq(TENANT_ID),
                        eq(MAILBOX_ID),
                        eq(MAILBOX_ID),
                        eq(MESSAGE_ID),
                        eq(THREAD_ID),
                        any(),
                        any(),
                        any(UUID.class),
                        anyString(),
                        any(),
                        any(),
                        eq("on_demand_draft")))
                .thenReturn(Optional.empty());
        when(triageAuditWriter.findPendingAuditId(
                        eq(TENANT_ID),
                        eq(MAILBOX_ID),
                        eq(MESSAGE_ID),
                        any(UUID.class),
                        any(),
                        any()))
                .thenReturn(Optional.of(AUDIT_ID));
        when(triageAuditRepository.reclaimStalePending(eq(AUDIT_ID), eq(TENANT_ID), anyString()))
                .thenReturn(0);

        assertThatThrownBy(
                        () ->
                                service.generateOrRegenerate(
                                        new GenerateThreadDraftCommand(TENANT_ID, THREAD_ID)))
                .isInstanceOf(DraftGenerationInFlightException.class);

        verify(triageGmailWriter, never())
                .saveDraft(
                        any(MailboxRef.class), any(ReplyHeaders.class), anyString(), anyString());
        verify(triageAuditRepository, never()).markApplied(any(), any(), anyString(), any(), any());
        verify(lockHandle).release();
    }

    @Test
    void regeneration_saves_new_draft_before_deleting_old_draft() throws Exception {
        arrangeSuccessfulRegeneration();

        GenerateThreadDraftResult result =
                service.generateOrRegenerate(new GenerateThreadDraftCommand(TENANT_ID, THREAD_ID));

        assertThat(result.status()).isEqualTo(DraftStatus.REGENERATED);
        InOrder gmailWriteOrder = inOrder(triageGmailWriter);
        gmailWriteOrder
                .verify(triageGmailWriter)
                .saveDraft(
                        eq(MAILBOX_REF),
                        any(ReplyHeaders.class),
                        eq("generated draft body"),
                        eq(THREAD_ID));
        gmailWriteOrder.verify(triageGmailWriter).deleteDraft(MAILBOX_REF, OLD_DRAFT_ID);
    }

    @Test
    void delete_failure_after_success_is_logged_not_propagated() throws Exception {
        arrangeSuccessfulRegeneration();
        doThrow(new IOException("delete failed"))
                .when(triageGmailWriter)
                .deleteDraft(MAILBOX_REF, OLD_DRAFT_ID);

        GenerateThreadDraftResult result =
                service.generateOrRegenerate(new GenerateThreadDraftCommand(TENANT_ID, THREAD_ID));

        assertThat(result.status()).isEqualTo(DraftStatus.REGENERATED);
    }

    @Test
    void save_draft_failure_leaves_existing_draft_intact() throws Exception {
        arrangeLock();
        when(classifyThreadReplyStatusService.currentDraftId(THREAD_ID))
                .thenReturn(Optional.of(OLD_DRAFT_ID));
        when(draftReplySourceLoader.load(MAILBOX_REF, THREAD_ID)).thenReturn(source());
        when(draftBodyGenerator.generate(
                        TENANT_ID, MAILBOX_REF, THREAD_ID, "inbound body", "Inbound subject"))
                .thenReturn("generated draft body");
        when(triageAuditWriter.insertPending(
                        any(UUID.class),
                        any(UUID.class),
                        any(UUID.class),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class),
                        anyString(),
                        any(),
                        any(),
                        anyString()))
                .thenReturn(Optional.of(AUDIT_ID));
        when(triageGmailWriter.saveDraft(
                        eq(MAILBOX_REF),
                        any(ReplyHeaders.class),
                        eq("generated draft body"),
                        eq(THREAD_ID)))
                .thenThrow(new IOException("save failed"));

        assertThatThrownBy(
                        () ->
                                service.generateOrRegenerate(
                                        new GenerateThreadDraftCommand(TENANT_ID, THREAD_ID)))
                .isInstanceOf(DraftGenerationFailedException.class);

        verify(triageGmailWriter, never()).deleteDraft(any(MailboxRef.class), anyString());
        verify(classifyThreadReplyStatusService, never()).classify(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(lockHandle).release();
    }

    @Test
    void safety_violation_causes_zero_gmail_writes_or_persistence() throws Exception {
        arrangeLock();
        when(classifyThreadReplyStatusService.currentDraftId(THREAD_ID))
                .thenReturn(Optional.empty());
        when(draftReplySourceLoader.load(MAILBOX_REF, THREAD_ID)).thenReturn(source());
        when(draftBodyGenerator.generate(
                        TENANT_ID, MAILBOX_REF, THREAD_ID, "inbound body", "Inbound subject"))
                .thenThrow(new SafetyViolationException());

        assertThatThrownBy(
                        () ->
                                service.generateOrRegenerate(
                                        new GenerateThreadDraftCommand(TENANT_ID, THREAD_ID)))
                .isInstanceOf(SafetyViolationException.class);

        verify(triageGmailWriter, never())
                .saveDraft(
                        any(MailboxRef.class), any(ReplyHeaders.class), anyString(), anyString());
        verify(triageAuditRepository, never()).markApplied(any(), any(), anyString(), any(), any());
        verify(lockHandle).release();
    }

    @Test
    void held_lock_raises_in_flight_exception() {
        when(redisDistributedLock.tryAcquire(anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.generateOrRegenerate(
                                        new GenerateThreadDraftCommand(TENANT_ID, THREAD_ID)))
                .isInstanceOf(DraftGenerationInFlightException.class);
    }

    @Test
    void unavailable_lock_backend_raises_retryable_unavailable_exception() {
        when(redisDistributedLock.tryAcquire(anyString(), any()))
                .thenThrow(new LockBackendUnavailableException());

        assertThatThrownBy(
                        () ->
                                service.generateOrRegenerate(
                                        new GenerateThreadDraftCommand(TENANT_ID, THREAD_ID)))
                .isInstanceOf(DraftGenerationUnavailableException.class);
    }

    private void arrangeSuccessfulRegeneration() throws Exception {
        arrangeLock();
        when(classifyThreadReplyStatusService.currentDraftId(THREAD_ID))
                .thenReturn(Optional.of(OLD_DRAFT_ID));
        when(draftReplySourceLoader.load(MAILBOX_REF, THREAD_ID)).thenReturn(source());
        when(draftBodyGenerator.generate(
                        TENANT_ID, MAILBOX_REF, THREAD_ID, "inbound body", "Inbound subject"))
                .thenReturn("generated draft body");
        when(triageGmailWriter.saveDraft(
                        eq(MAILBOX_REF),
                        any(ReplyHeaders.class),
                        eq("generated draft body"),
                        eq(THREAD_ID)))
                .thenReturn(NEW_DRAFT_ID);
        when(triageAuditWriter.insertPending(
                        any(UUID.class),
                        any(UUID.class),
                        any(UUID.class),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(UUID.class),
                        anyString(),
                        any(),
                        any(),
                        anyString()))
                .thenReturn(Optional.of(AUDIT_ID));
    }

    private void arrangeLock() {
        when(redisDistributedLock.tryAcquire(anyString(), any()))
                .thenReturn(Optional.of(lockHandle));
    }

    private static DraftReplySource source() {
        return new DraftReplySource(
                MESSAGE_ID,
                THREAD_ID,
                ReplyHeaders.of(
                        "<message-1@example.test>",
                        "",
                        "Inbound subject",
                        "sender@example.test",
                        THREAD_ID),
                "inbound body",
                "Inbound subject",
                false,
                false,
                false);
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
