package com.zeromail.core.draft.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.chat.usecases.settings.AssistantDraftSettingsService;
import com.zeromail.core.draft.domain.ToneContext;
import com.zeromail.core.llm.domain.Action;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.SanitizationContext;
import com.zeromail.core.llm.usecases.ToolCallResult;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.TriageDraftBodyGenerator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DraftBodyGenerator implements TriageDraftBodyGenerator {

    private static final Logger log = LoggerFactory.getLogger(DraftBodyGenerator.class);

    private final SanitizationPipeline sanitizationPipeline;
    private final ToneContextBuilder toneContextBuilder;
    private final LlmGateway llmGateway;
    private final Function<UUID, Optional<String>> emailSignatureResolver;

    public DraftBodyGenerator(
            SanitizationPipeline sanitizationPipeline,
            ToneContextBuilder toneContextBuilder,
            LlmGateway llmGateway) {
        this(sanitizationPipeline, toneContextBuilder, llmGateway, _ -> Optional.empty());
    }

    @Autowired
    public DraftBodyGenerator(
            SanitizationPipeline sanitizationPipeline,
            ToneContextBuilder toneContextBuilder,
            LlmGateway llmGateway,
            AssistantDraftSettingsService assistantDraftSettingsService) {
        this(
                sanitizationPipeline,
                toneContextBuilder,
                llmGateway,
                assistantDraftSettingsService::emailSignature);
    }

    DraftBodyGenerator(
            SanitizationPipeline sanitizationPipeline,
            ToneContextBuilder toneContextBuilder,
            LlmGateway llmGateway,
            Function<UUID, Optional<String>> emailSignatureResolver) {
        this.sanitizationPipeline =
                Objects.requireNonNull(
                        sanitizationPipeline, "sanitizationPipeline must not be null");
        this.toneContextBuilder =
                Objects.requireNonNull(toneContextBuilder, "toneContextBuilder must not be null");
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway must not be null");
        this.emailSignatureResolver =
                Objects.requireNonNull(
                        emailSignatureResolver, "emailSignatureResolver must not be null");
    }

    @Override
    public String generate(
            UUID tenantId, String gmailThreadId, String inboundRawHtml, String inboundSubject) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        String threadId = requireText(gmailThreadId, "gmailThreadId");
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                generateWithTenantBound(
                                        tenantId, null, threadId, inboundRawHtml, inboundSubject));
    }

    @Override
    public String generate(
            UUID tenantId,
            MailboxRef mailboxRef,
            String gmailThreadId,
            String inboundRawHtml,
            String inboundSubject) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(mailboxRef, "mailboxRef must not be null");
        if (!tenantId.equals(mailboxRef.tenantId())) {
            throw new IllegalArgumentException("mailboxRef tenantId must match tenantId");
        }
        String threadId = requireText(gmailThreadId, "gmailThreadId");
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                generateWithTenantBound(
                                        tenantId,
                                        mailboxRef,
                                        threadId,
                                        inboundRawHtml,
                                        inboundSubject));
    }

    private String generateWithTenantBound(
            UUID tenantId,
            MailboxRef mailboxRef,
            String gmailThreadId,
            String inboundRawHtml,
            String inboundSubject) {
        SanitizationContext sanitizedInbound = sanitizationPipeline.sanitize(inboundRawHtml);
        ToneContext toneContext =
                mailboxRef == null
                        ? toneContextBuilder.buildForCurrentTenant()
                        : toneContextBuilder.buildForMailbox(mailboxRef);
        ToolCallResult toolCallResult =
                llmGateway.chatForDraft(
                        CallSite.DRAFT,
                        sanitizedInbound,
                        toneContext.descriptorBlock(),
                        toneContext.styleSnippets(),
                        Objects.requireNonNullElse(inboundSubject, ""));
        if (toolCallResult.action() != Action.SAVE_DRAFT) {
            throw new SafetyViolationException();
        }
        Object draftBody = toolCallResult.args().get("body");
        if (!(draftBody instanceof String body) || body.isBlank()) {
            throw new SafetyViolationException();
        }
        String generatedDraftBody = appendEmailSignature(tenantId, body.trim());
        String logSafeGmailThreadId = gmailThreadId.replaceAll("[\\r\\n]", "");
        log.info(
                "event=draft_body_generated tenantId={} gmailThreadId={}",
                tenantId,
                logSafeGmailThreadId);
        return generatedDraftBody;
    }

    private String appendEmailSignature(UUID tenantId, String generatedDraftBody) {
        return emailSignatureResolver
                .apply(tenantId)
                .map(emailSignature -> generatedDraftBody + "\n\n" + emailSignature)
                .orElse(generatedDraftBody);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}
