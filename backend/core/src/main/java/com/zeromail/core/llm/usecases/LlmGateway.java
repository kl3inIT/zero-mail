package com.zeromail.core.llm.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.exception.LlmEvaluationFailedException;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.exception.TokenBudgetExceededException;
import java.util.List;
import java.util.Map;

/**
 * Single chokepoint for all LLM traffic in Zero Mail. Phase 3 (Rules Engine) and Phase 4 (Triage)
 * import this interface verbatim.
 *
 * <p><b>Cross-phase contract.</b> {@link #chat(CallSite, String)} sanitizes input through the Plan
 * 02 pipeline, prepends the fixed system prompt declaring email content as data, uses the
 * gateway-owned fixed tool allow-list {label, archive, save_draft}, enforces tool-call allow-list
 * validation in Plan 04, routes through BYOK or the platform path in Plan 05, and wraps platform
 * calls with Phase 2B credit reservation in Plan 06.
 *
 * <p><b>Privacy invariant.</b> Implementations MUST NOT log, persist, or expose raw email content,
 * prompt text, or completion text. Observation spans carry metadata only: provider, model, token
 * counts, latency, stop reason, and truncation state.
 */
public interface LlmGateway {

    // Gateway owns tools; callers cannot pass arbitrary tool definitions.
    ToolCallResult chat(CallSite callSite, String rawHtml);

    /**
     * Draft-reply path. Callers pass only sanitized inbound content plus primitive tone hints; the
     * gateway owns the draft-only tool profile and must return exactly one {@code save_draft} tool
     * call.
     */
    ToolCallResult chatForDraft(
            CallSite callSite,
            SanitizationContext inbound,
            String toneDescriptorBlock,
            List<String> toneStyleSnippets,
            String inboundSubject);

    /**
     * Rules-engine compile path. The gateway owns the rule-compile tool profile; callers pass only
     * a sanitized compiler payload and never pass arbitrary tool definitions.
     */
    RuleCompileGatewayResult compileRule(CallSite callSite, String compilerPayload);

    /**
     * Rules-engine compile path for UX review drafts. It uses the same canonical {@code
     * rule_compile} tool name but a stricter schema that removes the clarification branch so broad
     * semantic rules become editable form drafts instead of blocking follow-up questions.
     */
    default RuleCompileGatewayResult compileRuleReviewDraft(
            CallSite callSite, String compilerPayload) {
        return compileRule(callSite, compilerPayload);
    }

    /**
     * Preview text-generation path for user-reviewed settings helpers. Implementations keep the
     * prompt and model output in memory only; audit rows, if written, are usage metadata only.
     */
    default String generatePreviewText(
            CallSite callSite, String systemPrompt, String userMessage, int maxTokens) {
        throw new UnsupportedOperationException("Preview text generation is unavailable");
    }

    /**
     * Resolves a batch of {@code SEMANTIC_INTENT} matchers for one message in one structured-output
     * LLM call.
     *
     * <p>The {@code rawMessageContent} parameter is whatever sanitizable text the caller has. In
     * the Phase 4 triage path, the caller builds {@code semanticEvalContent} from {@code
     * RuleEvaluationInput.sanitizedSubjectExcerpt()} plus a deterministic content-free flag
     * summary; the Gmail metadata-only fetch carries no body. Implementations re-run the Phase 2C
     * sanitization pipeline and perform the prompt-budget check before reserving platform credits.
     * BYOK callers bypass the platform credit ledger.
     *
     * @throws TokenBudgetExceededException when sanitized content plus intent/schema overhead
     *     exceeds the 3896-token cap before any model call
     * @throws SafetyViolationException when the model returns an unknown or missing node id
     * @throws LlmEvaluationFailedException when the gateway's internal retry path is exhausted
     */
    Map<String, Boolean> evaluateSemanticIntents(
            CallSite callSite, String rawMessageContent, List<SemanticIntentRequest> intents);

    /**
     * Drift detection path. It uses the same sanitization pipeline, system prompt, and allow-list
     * as {@link #chat(CallSite, String)}, but is pinned to the drift model and bypasses future user
     * credit reservation.
     */
    ToolCallResult driftCheck(String rawEmailFixture);
}
