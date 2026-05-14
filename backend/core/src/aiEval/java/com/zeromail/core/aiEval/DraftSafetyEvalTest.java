package com.zeromail.core.aiEval;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.zeromail.core.draft.usecases.DraftBodyGenerator;
import com.zeromail.core.draft.usecases.DraftReplySourceLoader;
import com.zeromail.core.draft.usecases.DraftReplySourceLoader.DraftReplySource;
import com.zeromail.core.draft.usecases.GenerateThreadDraftCommand;
import com.zeromail.core.draft.usecases.GenerateThreadDraftService;
import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.domain.ActionValidator;
import com.zeromail.core.llm.domain.AllowListedTools;
import com.zeromail.core.llm.domain.LlmToolProfile;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.usecases.LlmTool;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class DraftSafetyEvalTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-00000005b007");
    private static final String THREAD_ID = "safety-thread";

    @Test
    void dim4_action_validator_accepts_exact_allow_list_and_rejects_everything_else() {
        ActionValidator actionValidator = new ActionValidator();

        assertThat(actionValidator.validate("label")).isEqualTo(Action.LABEL);
        assertThat(actionValidator.validate("archive")).isEqualTo(Action.ARCHIVE);
        assertThat(actionValidator.validate("save_draft")).isEqualTo(Action.SAVE_DRAFT);

        for (String unsafeAction :
                List.of("", "send", "drafts.send", "drafts.update", "messages.send", "reply")) {
            assertThatThrownBy(() -> actionValidator.validate(unsafeAction))
                    .isInstanceOf(SafetyViolationException.class)
                    .hasMessage(null);
        }
    }

    @Test
    void dim4_save_draft_only_tool_schema_is_body_only() {
        List<LlmTool> saveDraftTools = new AllowListedTools().tools(LlmToolProfile.SAVE_DRAFT_ONLY);

        assertThat(saveDraftTools).singleElement().satisfies(DraftSafetyEvalTest::assertBodyOnly);
    }

    @Test
    void dim4_safety_violation_performs_zero_gmail_writes_or_persistence() throws Exception {
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
        when(threadReplyStatusRepository.findByGmailThreadId(THREAD_ID))
                .thenReturn(Optional.empty());
        when(draftReplySourceLoader.load(TENANT_ID, THREAD_ID)).thenReturn(source());
        when(draftBodyGenerator.generate(any(), anyString(), anyString(), anyString()))
                .thenThrow(new SafetyViolationException());
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
                        Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(
                        () ->
                                service.generateOrRegenerate(
                                        new GenerateThreadDraftCommand(TENANT_ID, THREAD_ID)))
                .isInstanceOf(SafetyViolationException.class)
                .hasMessage(null);

        verify(triageGmailWriter, never()).saveDraft(any(), any(), anyString(), anyString());
        verify(triageAuditWriter, never())
                .insertPending(
                        any(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        any(),
                        anyString());
        verify(triageAuditRepository, never()).markApplied(any(), any(), anyString(), any(), any());
        verify(classifyThreadReplyStatusService, never()).classify(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(lockHandle).release();
    }

    @Test
    void dim4_draft_and_triage_paths_never_send_or_update_gmail_messages() {
        noClasses()
                .that()
                .resideInAnyPackage("..core.draft..", "..core.triage..")
                .should(
                        new ArchCondition<JavaClass>(
                                "call Gmail.Users.Messages.send, Gmail.Users.Drafts.send, or Gmail.Users.Drafts.update") {
                            @Override
                            public void check(JavaClass javaClass, ConditionEvents events) {
                                javaClass
                                        .getMethodCallsFromSelf()
                                        .forEach(
                                                methodCall -> {
                                                    String ownerName =
                                                            methodCall
                                                                    .getTargetOwner()
                                                                    .getName()
                                                                    .replace('$', '.');
                                                    String methodName = methodCall.getName();
                                                    boolean messagesSend =
                                                            ownerName.endsWith(
                                                                            "Gmail.Users.Messages")
                                                                    && methodName.equals("send");
                                                    boolean draftsSendOrUpdate =
                                                            ownerName.endsWith("Gmail.Users.Drafts")
                                                                    && (methodName.equals("send")
                                                                            || methodName.equals(
                                                                                    "update"));
                                                    if (messagesSend || draftsSendOrUpdate) {
                                                        events.add(
                                                                SimpleConditionEvent.violated(
                                                                        methodCall,
                                                                        "Forbidden Gmail send/update call at "
                                                                                + methodCall
                                                                                        .getSourceCodeLocation()));
                                                    }
                                                });
                            }
                        })
                .because("DRFT-04 allows saving Gmail drafts only; send/edit stay in Gmail.")
                .allowEmptyShould(true)
                .check(
                        new ClassFileImporter()
                                .withImportOption(new ImportOption.DoNotIncludeTests())
                                .importPackages("com.zeromail"));
    }

    @SuppressWarnings("unchecked")
    private static void assertBodyOnly(LlmTool tool) {
        assertThat(tool.name()).isEqualTo("save_draft");
        assertThat(tool.jsonSchema()).containsEntry("type", "object");
        assertThat((List<String>) tool.jsonSchema().get("required")).containsExactly("body");
        Map<String, Object> properties = (Map<String, Object>) tool.jsonSchema().get("properties");
        assertThat(properties).containsOnlyKeys("body");
        assertThat((Map<String, Object>) properties.get("body")).containsEntry("type", "string");
    }

    private static DraftReplySource source() {
        return new DraftReplySource(
                "safety-message",
                THREAD_ID,
                ReplyHeaders.of(
                        "<safety-message@synthetic.test>",
                        null,
                        "Safety subject",
                        "sender@synthetic.test",
                        THREAD_ID),
                "Synthetic inbound body",
                "Safety subject",
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
