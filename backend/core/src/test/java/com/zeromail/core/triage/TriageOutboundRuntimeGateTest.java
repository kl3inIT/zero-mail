package com.zeromail.core.triage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.billing.usecases.CreditLedger;
import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.rules.domain.RuleEvaluationInput;
import com.zeromail.core.rules.projection.EnabledRuleSnapshot;
import com.zeromail.core.rules.projection.RuleAutomationSettingsView;
import com.zeromail.core.rules.usecases.RuleAutomationSettingsService;
import com.zeromail.core.rules.usecases.RuleManagementService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.usecases.TenantService;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
import com.zeromail.core.triage.usecases.TriageAuditSaga;
import com.zeromail.core.triage.usecases.TriageAuditSaga.GmailWriteResult;
import com.zeromail.core.triage.usecases.TriageAuditSaga.ReservePhaseResult;
import com.zeromail.core.triage.usecases.TriageAuditSaga.TriageAuditCommand;
import com.zeromail.core.triage.usecases.TriageDraftBodyGenerator;
import com.zeromail.core.triage.usecases.TriageOrchestratorService;
import com.zeromail.core.triage.usecases.TriageRuleEvaluationInputFactory;
import com.zeromail.core.triage.usecases.TriageRuleEvaluationInputFactory.TriageRuleEvaluationInput;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

class TriageOutboundRuntimeGateTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000008101");
    private static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000008102");
    private static final UUID AUDIT_ID = UUID.fromString("00000000-0000-0000-0000-000000008103");
    private static final String GMAIL_MESSAGE_ID = "gmail-message-outbound-gate";
    private static final String GMAIL_THREAD_ID = "gmail-thread-outbound-gate";

    private final TenantService tenantService = mock(TenantService.class);
    private final TriageRuleEvaluationInputFactory triageRuleEvaluationInputFactory =
            mock(TriageRuleEvaluationInputFactory.class);
    private final RuleManagementService ruleManagementService = mock(RuleManagementService.class);
    private final RuleAutomationSettingsService ruleAutomationSettingsService =
            mock(RuleAutomationSettingsService.class);
    private final LlmGateway llmGateway = mock(LlmGateway.class);
    private final CreditLedger creditLedger = mock(CreditLedger.class);
    private final SenderSafetyNetService senderSafetyNetService =
            mock(SenderSafetyNetService.class);
    private final TriageAuditSaga triageAuditSaga = mock(TriageAuditSaga.class);
    private final TriageDraftBodyGenerator draftBodyGenerator =
            mock(TriageDraftBodyGenerator.class);
    private final ClassifyThreadReplyStatusService classifyThreadReplyStatusService =
            mock(ClassifyThreadReplyStatusService.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);

    @Test
    void global_auto_send_disabled_saves_draft_and_never_sends() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(false, false, senderDomainMatcher(), sendEmailAction());
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.of(AUDIT_ID), true));
        when(triageAuditSaga.outboundDraftFallbackPhase(
                        any(TriageAuditCommand.class), eq(AUDIT_ID), eq("AUTO_SEND_DISABLED")))
                .thenReturn(applied("draft-auto-disabled"));

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(triageAuditSaga, never())
                .outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID));
        verify(triageAuditSaga)
                .outboundDraftFallbackPhase(
                        any(TriageAuditCommand.class), eq(AUDIT_ID), eq("AUTO_SEND_DISABLED"));
        verifyFinalizedApplied();
    }

    @Test
    void protected_sender_falls_back_to_draft_for_outbound_actions() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, true, senderDomainMatcher(), sendReplyAction());
        when(draftBodyGenerator.generate(eq(TENANT_ID), eq(GMAIL_THREAD_ID), any(), any()))
                .thenReturn("Generated safe reply");
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.of(AUDIT_ID), true));
        when(triageAuditSaga.outboundDraftFallbackPhase(
                        any(TriageAuditCommand.class), eq(AUDIT_ID), eq("SENDER_SAFETY_NET")))
                .thenReturn(applied("draft-sender-net"));

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(triageAuditSaga, never())
                .outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID));
        verify(triageAuditSaga)
                .outboundDraftFallbackPhase(
                        any(TriageAuditCommand.class), eq(AUDIT_ID), eq("SENDER_SAFETY_NET"));
    }

    @Test
    void low_trust_static_matcher_falls_back_to_draft_for_outbound_actions() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, false, subjectMatcher(), sendEmailAction());
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.of(AUDIT_ID), true));
        when(triageAuditSaga.outboundDraftFallbackPhase(
                        any(TriageAuditCommand.class), eq(AUDIT_ID), eq("LOW_TRUST_STATIC_FROM")))
                .thenReturn(applied("draft-low-trust"));

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(triageAuditSaga, never())
                .outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID));
        verify(triageAuditSaga)
                .outboundDraftFallbackPhase(
                        any(TriageAuditCommand.class), eq(AUDIT_ID), eq("LOW_TRUST_STATIC_FROM"));
    }

    @Test
    void outbound_send_failure_falls_back_to_draft() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, false, senderDomainMatcher(), sendEmailAction());
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.of(AUDIT_ID), true));
        when(triageAuditSaga.outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID)))
                .thenThrow(new IOException("missing send scope"));
        when(triageAuditSaga.outboundDraftFallbackPhase(
                        any(TriageAuditCommand.class), eq(AUDIT_ID), eq("OUTBOUND_SEND_FAILED")))
                .thenReturn(applied("draft-send-failed"));

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(triageAuditSaga).outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID));
        verify(triageAuditSaga)
                .outboundDraftFallbackPhase(
                        any(TriageAuditCommand.class), eq(AUDIT_ID), eq("OUTBOUND_SEND_FAILED"));
    }

    @Test
    void skipped_reservation_prevents_duplicate_outbound_send_attempt() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, false, senderDomainMatcher(), sendEmailAction());
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.empty(), false));

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(triageAuditSaga, never())
                .outboundSendPhase(any(TriageAuditCommand.class), any(UUID.class));
        verify(triageAuditSaga, never())
                .outboundDraftFallbackPhase(any(TriageAuditCommand.class), any(UUID.class), any());
    }

    @Test
    void tenant_context_mismatch_fails_audit_before_any_gmail_send_or_draft() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, false, senderDomainMatcher(), sendEmailAction());

        verifyTenantMismatchBlocksGmailWrites(orchestratorService);
    }

    @Test
    void tenant_context_mismatch_wins_when_auto_send_is_disabled() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(false, false, senderDomainMatcher(), sendEmailAction());

        verifyTenantMismatchBlocksGmailWrites(orchestratorService);
    }

    @Test
    void tenant_context_mismatch_wins_when_sender_is_protected() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, true, senderDomainMatcher(), sendEmailAction());

        verifyTenantMismatchBlocksGmailWrites(orchestratorService);
    }

    @Test
    void tenant_context_mismatch_wins_when_rule_is_low_trust() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, false, subjectMatcher(), sendEmailAction());

        verifyTenantMismatchBlocksGmailWrites(orchestratorService);
    }

    private TriageOrchestratorService orchestratorService(
            boolean autoSendRulesEnabled,
            boolean senderProtected,
            String matcherAstJson,
            String actionIntentsJson)
            throws Exception {
        when(tenantService.triageSettingsFor(TENANT_ID))
                .thenReturn(TenantService.TenantTriageSettings.defaults());
        when(triageRuleEvaluationInputFactory.fetch(any(MailMessageObserved.class)))
                .thenReturn(Optional.of(triageInput()));
        when(ruleManagementService.listEnabledForExecution(TENANT_ID))
                .thenReturn(
                        List.of(
                                new EnabledRuleSnapshot(
                                        RULE_ID,
                                        "Outbound rule",
                                        10,
                                        matcherAstJson,
                                        actionIntentsJson)));
        when(ruleAutomationSettingsService.getOrCreate(TENANT_ID))
                .thenReturn(new RuleAutomationSettingsView(autoSendRulesEnabled));
        when(senderSafetyNetService.isProtected(TENANT_ID, "sender@example.com"))
                .thenReturn(senderProtected);

        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
        when(meterRegistryProvider.getIfAvailable(any())).thenReturn(new SimpleMeterRegistry());

        return new TriageOrchestratorService(
                tenantService,
                triageRuleEvaluationInputFactory,
                ruleManagementService,
                ruleAutomationSettingsService,
                llmGateway,
                creditLedger,
                senderSafetyNetService,
                triageAuditSaga,
                draftBodyGenerator,
                classifyThreadReplyStatusService,
                transactionManager,
                meterRegistryProvider);
    }

    private static TriageRuleEvaluationInput triageInput() {
        Instant observedAt = Instant.parse("2026-05-23T00:00:00Z");
        RuleEvaluationInput ruleEvaluationInput =
                new RuleEvaluationInput(
                        "sender@example.com",
                        "example.com",
                        List.of("me@example.com"),
                        List.of(),
                        "Planning update",
                        List.of("INBOX"),
                        List.of("personal"),
                        observedAt,
                        observedAt,
                        false,
                        false,
                        false,
                        Optional.empty(),
                        Set.of());
        return new TriageRuleEvaluationInput(
                ruleEvaluationInput,
                "sender@example.com",
                GMAIL_THREAD_ID,
                "<inbound@example.com>",
                null,
                "sender@example.com");
    }

    private static MailMessageObserved observedEvent() {
        return new MailMessageObserved(
                TENANT_ID,
                GMAIL_MESSAGE_ID,
                GMAIL_THREAD_ID,
                Instant.parse("2026-05-23T00:00:00Z"));
    }

    private static String senderDomainMatcher() {
        return """
                {"schemaVersion":"rules.v1","type":"SENDER_DOMAIN","nodeId":"sender-domain","domain":"example.com"}
                """;
    }

    private static String subjectMatcher() {
        return """
                {"schemaVersion":"rules.v1","type":"SUBJECT_CONTAINS","nodeId":"subject","text":"Planning"}
                """;
    }

    private static String sendEmailAction() {
        return """
                [{"type":"send_email","to":["safe@example.com"],"subject":"Update","body":"Body"}]
                """;
    }

    private static String sendReplyAction() {
        return """
                [{"type":"send_reply","instruction":"Reply with a short confirmation"}]
                """;
    }

    private static GmailWriteResult applied(String externalReference) {
        return new GmailWriteResult(true, externalReference, null, null, null);
    }

    private void verifyFinalizedApplied() {
        ArgumentCaptor<GmailWriteResult> writeResultCaptor =
                ArgumentCaptor.forClass(GmailWriteResult.class);
        verify(triageAuditSaga)
                .finalizePhase(eq(TENANT_ID), eq(AUDIT_ID), writeResultCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(writeResultCaptor.getValue().applied()).isTrue();
    }

    private void verifyTenantMismatchBlocksGmailWrites(
            TriageOrchestratorService orchestratorService) throws IOException {
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.of(AUDIT_ID), true));

        withTenant(
                UUID.randomUUID(), () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(triageAuditSaga, never())
                .outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID));
        verify(triageAuditSaga, never())
                .outboundDraftFallbackPhase(any(TriageAuditCommand.class), eq(AUDIT_ID), any());
        ArgumentCaptor<GmailWriteResult> writeResultCaptor =
                ArgumentCaptor.forClass(GmailWriteResult.class);
        verify(triageAuditSaga)
                .finalizePhase(eq(TENANT_ID), eq(AUDIT_ID), writeResultCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(writeResultCaptor.getValue().failureReason())
                .isEqualTo("TENANT_CONTEXT_MISMATCH");
    }

    private static void withTenant(UUID tenantId, TenantRunnable tenantRunnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(tenantRunnable::run);
    }

    @FunctionalInterface
    private interface TenantRunnable {
        void run();
    }
}
