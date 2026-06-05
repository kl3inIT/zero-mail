package com.zeromail.core.rules.domain;

/**
 * Single source of truth for the {@code semanticEvalContent} string sent to the LLM when a
 * SEMANTIC_INTENT matcher is evaluated.
 *
 * <p>Both the live triage runtime ({@code TriageOrchestratorService}) and the rule test/preview
 * ({@code RulePreviewService}) MUST build this content here. Previously each owned its own builder
 * with a different field set and key format (runtime emitted {@code subjectExcerpt=…}, {@code
 * labelCount}, {@code categories}, {@code internalDate}; preview emitted {@code subject: …} and
 * only the flags that happened to be true). The LLM therefore received a different prompt for the
 * same message in test vs. runtime, so a rule could test as "not matched" yet act on the same email
 * at runtime. Sharing one builder guarantees test ≡ runtime for the same {@link
 * RuleEvaluationInput}.
 *
 * <p>Privacy (v1): sanitized subject excerpt plus deterministic content-free metadata flags ONLY.
 * Do not extend this with body, snippet, raw header, sender display-name, prompt, completion, or
 * drafted-reply content without a new privacy review — this string is sent to the model.
 */
public final class SemanticEvalContentBuilder {

    private SemanticEvalContentBuilder() {}

    public static String build(RuleEvaluationInput ruleEvaluationInput) {
        // NOTE: do NOT add body/snippet/raw-header fetch here.
        return String.join(
                "\n",
                "subjectExcerpt=" + ruleEvaluationInput.sanitizedSubjectExcerpt(),
                "senderDomain=" + ruleEvaluationInput.sanitizedSenderDomain(),
                "labelCount=" + ruleEvaluationInput.gmailLabelIds().size(),
                "categories=" + String.join(",", ruleEvaluationInput.gmailCategories()),
                "hasAttachment=" + ruleEvaluationInput.hasAttachment(),
                "listUnsubscribePresent=" + ruleEvaluationInput.listUnsubscribePresent(),
                "newsletterIndicatorPresent=" + ruleEvaluationInput.newsletterIndicatorPresent(),
                "internalDate=" + ruleEvaluationInput.internalDate());
    }
}
