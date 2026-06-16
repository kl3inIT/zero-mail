package com.zeromail.core.triage.usecases;

import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailPreviewMessage;
import com.zeromail.core.rules.domain.RuleEvaluationInput;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TriageRuleEvaluationInputFactory {

    private final GmailPreviewReadService gmailPreviewReadService;

    public TriageRuleEvaluationInputFactory(GmailPreviewReadService gmailPreviewReadService) {
        this.gmailPreviewReadService = gmailPreviewReadService;
    }

    public Optional<TriageRuleEvaluationInput> fetch(MailMessageObserved observedEvent) {
        return fetch(
                observedEvent.tenantId(),
                observedEvent.gmailConnectionId(),
                observedEvent.gmailMessageId(),
                observedEvent.gmailThreadId(),
                observedEvent.observedAt());
    }

    public Optional<TriageRuleEvaluationInput> fetch(
            UUID tenantId,
            UUID gmailConnectionId,
            String gmailMessageId,
            String gmailThreadId,
            Instant observedAt) {
        return gmailPreviewReadService
                .fetchTriageInput(
                        tenantId, gmailConnectionId, gmailMessageId, gmailThreadId, observedAt)
                .map(TriageRuleEvaluationInputFactory::toTriageRuleEvaluationInput);
    }

    public Optional<TriageRuleEvaluationInput> fetch(
            UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt) {
        return gmailPreviewReadService
                .fetchTriageInput(tenantId, gmailMessageId, gmailThreadId, observedAt)
                .map(TriageRuleEvaluationInputFactory::toTriageRuleEvaluationInput);
    }

    private static TriageRuleEvaluationInput toTriageRuleEvaluationInput(
            GmailPreviewMessage previewMessage) {
        RuleEvaluationInput ruleEvaluationInput =
                new RuleEvaluationInput(
                        previewMessage.sanitizedSenderEmail(),
                        previewMessage.sanitizedSenderDomain(),
                        previewMessage.sanitizedToRecipientEmails(),
                        previewMessage.sanitizedCcRecipientEmails(),
                        previewMessage.sanitizedSubjectExcerpt(),
                        previewMessage.gmailLabelIds(),
                        previewMessage.gmailCategories(),
                        previewMessage.internalDate(),
                        previewMessage.observedAt(),
                        previewMessage.hasAttachment(),
                        previewMessage.listUnsubscribePresent(),
                        previewMessage.newsletterIndicatorPresent(),
                        previewMessage.autoReplyIndicatorPresent(),
                        previewMessage.sanitizedBodyEvidencePresent(),
                        previewMessage.bodyDerivedFlags());
        return new TriageRuleEvaluationInput(
                ruleEvaluationInput,
                previewMessage.sanitizedSenderEmail(),
                previewMessage.gmailThreadId(),
                previewMessage.rfcMessageId(),
                previewMessage.references(),
                previewMessage.replyToAddress());
    }

    public record TriageRuleEvaluationInput(
            RuleEvaluationInput evaluationInput,
            String sanitizedSenderEmail,
            String gmailThreadId,
            String rfcMessageId,
            String references,
            String replyToAddress) {}
}
