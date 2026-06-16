package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.usecases.CreditLedger;
import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.outbound.usecases.OutboundSendThrottle;
import com.zeromail.core.rules.domain.RuleEvaluationInput;
import com.zeromail.core.rules.projection.EnabledRuleSnapshot;
import com.zeromail.core.rules.projection.RuleAutomationSettingsView;
import com.zeromail.core.rules.usecases.RuleAutomationSettingsService;
import com.zeromail.core.rules.usecases.RuleManagementService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.usecases.TenantService;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.thread.usecases.ThreadReplyClassificationInput;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
import com.zeromail.core.triage.usecases.TriageAuditSaga;
import com.zeromail.core.triage.usecases.TriageAuditSaga.GmailWriteResult;
import com.zeromail.core.triage.usecases.TriageAuditSaga.ReservePhaseResult;
import com.zeromail.core.triage.usecases.TriageAuditSaga.TriageAuditCommand;
import com.zeromail.core.triage.usecases.TriageDraftBodyGenerator;
import com.zeromail.core.triage.usecases.TriageDraftSettings;
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
    private static final UUID GMAIL_CONNECTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000008104");
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
    private final TriageDraftSettings triageDraftSettings = mock(TriageDraftSettings.class);
    private final ClassifyThreadReplyStatusService classifyThreadReplyStatusService =
            mock(ClassifyThreadReplyStatusService.class);
    private final OutboundSendThrottle outboundSendThrottle = mock(OutboundSendThrottle.class);
    // Bare mock: TransactionTemplate.getTransaction returns null and commit is a no-op, so the
    // executeWithoutResult callback still runs synchronously in the test.
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);

    @Test
    void global_auto_send_disabled_fails_audit_and_never_sends() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(false, false, senderDomainMatcher(), sendEmailAction());
        verifyOutboundFailsWithReason(orchestratorService, "AUTO_SEND_DISABLED");
    }

    @Test
    void protected_sender_fails_audit_for_outbound_actions() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, true, senderDomainMatcher(), sendReplyAction());
        when(draftBodyGenerator.generate(
                        eq(TENANT_ID), any(MailboxRef.class), eq(GMAIL_THREAD_ID), any(), any()))
                .thenReturn("Generated safe reply");
        verifyOutboundFailsWithReason(orchestratorService, "SENDER_SAFETY_NET");
    }

    @Test
    void missing_sender_fails_audit_for_outbound_actions() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(
                        true, false, senderDomainMatcher(), sendEmailAction(), triageInput(null));
        verifyOutboundFailsWithReason(orchestratorService, "SENDER_SAFETY_NET");
    }

    @Test
    void auto_draft_replies_disabled_skips_save_draft_before_reserving_audit() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, false, senderDomainMatcher(), saveDraftAction());
        when(triageDraftSettings.autoDraftRepliesEnabled(TENANT_ID)).thenReturn(false);

        TriageOrchestratorService.OrchestrationResult orchestrationResult =
                withTenant(
                        TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        assertThat(orchestrationResult.appliedActions()).isZero();
        verify(draftBodyGenerator, never()).generate(any(), any(), any(), any());
        verify(triageAuditSaga, never()).reservePhase(any(TriageAuditCommand.class), any());
    }

    @Test
    void protected_sender_still_allows_safe_non_outbound_actions() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, true, senderDomainMatcher(), labelAction());
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.of(AUDIT_ID), true));
        when(triageAuditSaga.gmailWritePhase(any(TriageAuditCommand.class)))
                .thenReturn(applied("label-applied"));

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(triageAuditSaga).gmailWritePhase(any(TriageAuditCommand.class));
        verify(triageAuditSaga, never())
                .outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID));
        verifyFinalizedApplied();
    }

    @Test
    void intent_only_rule_sends_outbound_when_toggle_on_and_sender_not_protected()
            throws Exception {
        // The low-trust sender-anchor requirement was removed by product decision: an intent-only
        // matcher (no sender email/domain anchor) now auto-sends, gated only by the Auto-send
        // toggle and the user-managed safety-net list.
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, false, subjectMatcher(), sendEmailAction());
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.of(AUDIT_ID), true));
        when(triageAuditSaga.outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID)))
                .thenReturn(applied("sent-intent-rule"));

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(triageAuditSaga).outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID));
    }

    @Test
    void self_sent_message_skips_rules_pipeline_entirely() throws Exception {
        // Mail-loop guard: a message the tenant authored (label SENT) — e.g. the copy created by a
        // prior auto-send — must never re-enter the inbound rules engine, or an intent-only matcher
        // re-matches it and fires another send, looping unbounded. Nothing should be reserved/sent.
        TriageOrchestratorService orchestratorService =
                orchestratorService(
                        true,
                        false,
                        subjectMatcher(),
                        sendEmailAction(),
                        triageInputWithLabels(List.of("SENT")));

        TriageOrchestratorService.OrchestrationResult orchestrationResult =
                withTenant(
                        TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        assertThat(orchestrationResult.appliedActions()).isZero();
        verify(triageAuditSaga, never()).reservePhase(any(TriageAuditCommand.class), any());
        verify(triageAuditSaga, never())
                .outboundSendPhase(any(TriageAuditCommand.class), any(UUID.class));
        verify(outboundSendThrottle, never()).acquire(any());
    }

    @Test
    void automated_gmail_category_classifies_as_fyi_without_llm() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(
                        true,
                        false,
                        subjectMatcher(),
                        labelAction(),
                        triageInput(
                                "sender@example.com",
                                List.of("INBOX", "CATEGORY_PROMOTIONS"),
                                List.of("promotions"),
                                false));
        when(ruleManagementService.listEnabledForExecution(eq(TENANT_ID), any(UUID.class)))
                .thenReturn(List.of());

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(llmGateway, never()).classifyReplyNeeded(eq(CallSite.NEEDS_REPLY), anyString());
        ArgumentCaptor<ThreadReplyClassificationInput> classificationInputCaptor =
                ArgumentCaptor.forClass(ThreadReplyClassificationInput.class);
        verify(classifyThreadReplyStatusService).classify(classificationInputCaptor.capture());
        assertThat(classificationInputCaptor.getValue().inboundReplyNeeded()).isFalse();
        assertThat(classificationInputCaptor.getValue().lastMessageIsAutoReply()).isFalse();
    }

    @Test
    void inbound_auto_reply_classifies_as_fyi_without_llm_and_sets_auto_reply_flag()
            throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(
                        true,
                        false,
                        subjectMatcher(),
                        labelAction(),
                        triageInput(
                                "sender@example.com", List.of("INBOX"), List.of("personal"), true));
        when(ruleManagementService.listEnabledForExecution(eq(TENANT_ID), any(UUID.class)))
                .thenReturn(List.of());

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(llmGateway, never()).classifyReplyNeeded(eq(CallSite.NEEDS_REPLY), anyString());
        ArgumentCaptor<ThreadReplyClassificationInput> classificationInputCaptor =
                ArgumentCaptor.forClass(ThreadReplyClassificationInput.class);
        verify(classifyThreadReplyStatusService).classify(classificationInputCaptor.capture());
        assertThat(classificationInputCaptor.getValue().inboundReplyNeeded()).isFalse();
        assertThat(classificationInputCaptor.getValue().lastMessageIsAutoReply()).isTrue();
    }

    @Test
    void outbound_send_throttle_exhausted_fails_audit_and_never_sends() throws Exception {
        // Defense-in-depth: when the per-tenant auto-send cap is exhausted, the outbound action is
        // dropped (failed audit, no draft), bounding the blast radius of any runaway.
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, false, subjectMatcher(), sendEmailAction());
        when(outboundSendThrottle.acquire(TENANT_ID)).thenReturn(false);
        verifyOutboundFailsWithReason(orchestratorService, "OUTBOUND_RATE_LIMITED");
    }

    @Test
    void outbound_send_failure_fails_audit_without_drafting() throws Exception {
        TriageOrchestratorService orchestratorService =
                orchestratorService(true, false, senderDomainMatcher(), sendEmailAction());
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.of(AUDIT_ID), true));
        when(triageAuditSaga.outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID)))
                .thenThrow(new IOException("missing send scope"));

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        verify(triageAuditSaga).outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID));
        ArgumentCaptor<GmailWriteResult> writeResultCaptor =
                ArgumentCaptor.forClass(GmailWriteResult.class);
        verify(triageAuditSaga)
                .finalizePhase(eq(TENANT_ID), eq(AUDIT_ID), writeResultCaptor.capture());
        assertThat(writeResultCaptor.getValue().applied()).isFalse();
        assertThat(writeResultCaptor.getValue().failureReason()).isEqualTo("OUTBOUND_SEND_FAILED");
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
        return orchestratorService(
                autoSendRulesEnabled,
                senderProtected,
                matcherAstJson,
                actionIntentsJson,
                triageInput("sender@example.com"));
    }

    private TriageOrchestratorService orchestratorService(
            boolean autoSendRulesEnabled,
            boolean senderProtected,
            String matcherAstJson,
            String actionIntentsJson,
            TriageRuleEvaluationInput triageRuleEvaluationInput)
            throws Exception {
        when(tenantService.triageSettingsFor(TENANT_ID))
                .thenReturn(TenantService.TenantTriageSettings.defaults());
        when(triageRuleEvaluationInputFactory.fetch(any(MailMessageObserved.class)))
                .thenReturn(Optional.of(triageRuleEvaluationInput));
        when(ruleManagementService.listEnabledForExecution(eq(TENANT_ID), any(UUID.class)))
                .thenReturn(
                        List.of(
                                new EnabledRuleSnapshot(
                                        RULE_ID,
                                        "Outbound rule",
                                        10,
                                        matcherAstJson,
                                        actionIntentsJson)));
        when(ruleAutomationSettingsService.readOrDefault(TENANT_ID))
                .thenReturn(new RuleAutomationSettingsView(autoSendRulesEnabled));
        when(triageDraftSettings.autoDraftRepliesEnabled(TENANT_ID)).thenReturn(true);
        when(senderSafetyNetService.matchedProtectedPattern(TENANT_ID, "sender@example.com"))
                .thenReturn(senderProtected ? Optional.of("sender@example.com") : Optional.empty());
        // Default: throttle has budget. Individual tests override to exercise the rate-limit gate.
        when(outboundSendThrottle.acquire(TENANT_ID)).thenReturn(true);

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
                triageDraftSettings,
                classifyThreadReplyStatusService,
                outboundSendThrottle,
                transactionManager,
                meterRegistryProvider);
    }

    private static TriageRuleEvaluationInput triageInput(String sanitizedSenderEmail) {
        return triageInput(sanitizedSenderEmail, List.of("INBOX"));
    }

    private static TriageRuleEvaluationInput triageInputWithLabels(List<String> gmailLabelIds) {
        return triageInput("sender@example.com", gmailLabelIds, List.of("personal"), false);
    }

    private static TriageRuleEvaluationInput triageInput(
            String sanitizedSenderEmail, List<String> gmailLabelIds) {
        return triageInput(sanitizedSenderEmail, gmailLabelIds, List.of("personal"), false);
    }

    private static TriageRuleEvaluationInput triageInput(
            String sanitizedSenderEmail,
            List<String> gmailLabelIds,
            List<String> gmailCategories,
            boolean autoReplyIndicatorPresent) {
        Instant observedAt = Instant.parse("2026-05-23T00:00:00Z");
        RuleEvaluationInput ruleEvaluationInput =
                new RuleEvaluationInput(
                        "sender@example.com",
                        "example.com",
                        List.of("me@example.com"),
                        List.of(),
                        "Planning update",
                        gmailLabelIds,
                        gmailCategories,
                        observedAt,
                        observedAt,
                        false,
                        false,
                        false,
                        autoReplyIndicatorPresent,
                        Optional.empty(),
                        Set.of());
        return new TriageRuleEvaluationInput(
                ruleEvaluationInput,
                sanitizedSenderEmail,
                GMAIL_THREAD_ID,
                "<inbound@example.com>",
                null,
                "sender@example.com");
    }

    private static MailMessageObserved observedEvent() {
        return new MailMessageObserved(
                TENANT_ID,
                GMAIL_CONNECTION_ID,
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

    private static String labelAction() {
        return """
                [{"type":"label","labelName":"Needs review"}]
                """;
    }

    private static String saveDraftAction() {
        return """
                [{"type":"save_draft","instruction":"Draft a concise reply"}]
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
        ArgumentCaptor<GmailWriteResult> writeResultCaptor =
                ArgumentCaptor.forClass(GmailWriteResult.class);
        verify(triageAuditSaga)
                .finalizePhase(eq(TENANT_ID), eq(AUDIT_ID), writeResultCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(writeResultCaptor.getValue().failureReason())
                .isEqualTo("TENANT_CONTEXT_MISMATCH");
    }

    private void verifyOutboundFailsWithReason(
            TriageOrchestratorService orchestratorService, String expectedReason)
            throws IOException {
        when(triageAuditSaga.reservePhase(any(TriageAuditCommand.class), any()))
                .thenReturn(new ReservePhaseResult(Optional.of(AUDIT_ID), true));

        withTenant(TENANT_ID, () -> orchestratorService.processObservedEvent(observedEvent()));

        // Blocked outbound is never sent and never drafted — it is recorded as a failed audit
        // carrying the block reason (no Gmail draft is written).
        verify(triageAuditSaga, never())
                .outboundSendPhase(any(TriageAuditCommand.class), eq(AUDIT_ID));
        ArgumentCaptor<GmailWriteResult> writeResultCaptor =
                ArgumentCaptor.forClass(GmailWriteResult.class);
        verify(triageAuditSaga)
                .finalizePhase(eq(TENANT_ID), eq(AUDIT_ID), writeResultCaptor.capture());
        assertThat(writeResultCaptor.getValue().applied()).isFalse();
        assertThat(writeResultCaptor.getValue().failureReason()).isEqualTo(expectedReason);
    }

    private static <T> T withTenant(UUID tenantId, TenantSupplier<T> tenantSupplier) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantSupplier::get);
    }

    @FunctionalInterface
    private interface TenantSupplier<T> {
        T get();
    }
}
