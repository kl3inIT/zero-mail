package com.zeromail.core.rules.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.llm.service.LlmGateway;
import com.zeromail.core.rules.domain.MatcherEvaluationState;
import com.zeromail.core.rules.domain.MatcherNode;
import com.zeromail.core.rules.domain.RuleEvaluationInput;
import com.zeromail.core.rules.domain.RuleEvaluationResult;
import com.zeromail.core.rules.domain.SemanticIntentMatcher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleEvaluatorTest {

    private final RuleEvaluator ruleEvaluator = new RuleEvaluator();

    @Test
    void evaluates_every_deterministic_matcher_type_against_preview_safe_input() {
        RuleEvaluationInput ruleEvaluationInput = matchingInput();

        assertMatched(new MatcherNode.SenderEmailMatcher("sender-email", "billing@stripe.com"));
        assertMatched(new MatcherNode.SenderDomainMatcher("sender-domain", "stripe.com"));
        assertMatched(new MatcherNode.RecipientToMatcher("recipient-to", "founder@example.test"));
        assertMatched(new MatcherNode.RecipientCcMatcher("recipient-cc", "ops@example.test"));
        assertMatched(new MatcherNode.SubjectContainsMatcher("subject-contains", "receipt"));
        assertMatched(
                new MatcherNode.SubjectEqualsMatcher("subject-equals", "Receipt from Stripe"));
        assertMatched(
                new MatcherNode.SubjectRegexMatcher("subject-regex", "(?i)^receipt.*stripe$"));
        assertMatched(new MatcherNode.GmailLabelPresentMatcher("label-present", "INBOX"));
        assertMatched(new MatcherNode.GmailLabelAbsentMatcher("label-absent", "Label_999"));
        assertMatched(
                new MatcherNode.GmailCategoryPresentMatcher("category-present", "promotions"));
        assertMatched(new MatcherNode.GmailCategoryAbsentMatcher("category-absent", "social"));
        assertMatched(new MatcherNode.HasAttachmentMatcher("has-attachment"));
        assertMatched(new MatcherNode.ListUnsubscribePresentMatcher("list-unsubscribe"));
        assertMatched(new MatcherNode.NewsletterIndicatorMatcher("newsletter"));
        assertMatched(
                new MatcherNode.MessageAgeMatcher(
                        "age-newer", MatcherNode.MessageAgeOperator.NEWER_THAN_DAYS, 7));
        assertMatched(
                new MatcherNode.MessageDateMatcher(
                        "date-after",
                        MatcherNode.MessageDateOperator.AFTER,
                        LocalDate.parse("2026-05-01")));
        assertMatched(
                new MatcherNode.MessageDateMatcher(
                        "date-on",
                        MatcherNode.MessageDateOperator.ON,
                        LocalDate.parse("2026-05-08")));

        RuleEvaluationResult nonMatchedResult =
                ruleEvaluator.evaluate(
                        new MatcherNode.SenderDomainMatcher("sender-domain-miss", "github.com"),
                        ruleEvaluationInput);
        assertThat(nonMatchedResult.status()).isEqualTo(MatcherEvaluationState.NOT_MATCHED);
    }

    @Test
    void boolean_groups_apply_tri_state_semantics_in_deterministic_order() {
        RuleEvaluationInput ruleEvaluationInput = matchingInput();
        MatcherNode matchedMatcher = new MatcherNode.SenderDomainMatcher("matched", "stripe.com");
        MatcherNode missingMatcher =
                new MatcherNode.GmailLabelPresentMatcher("missing", "Label_999");
        MatcherNode deferredMatcher =
                new SemanticIntentMatcher(
                        "deferred", "messages that need a thoughtful reply", true);

        RuleEvaluationResult allMatchedResult =
                ruleEvaluator.evaluate(
                        new MatcherNode.AllMatcher("all", List.of(matchedMatcher, deferredMatcher)),
                        ruleEvaluationInput);
        RuleEvaluationResult anyMatchedResult =
                ruleEvaluator.evaluate(
                        new MatcherNode.AnyMatcher("any", List.of(missingMatcher, deferredMatcher)),
                        ruleEvaluationInput);
        RuleEvaluationResult anyWithMatchResult =
                ruleEvaluator.evaluate(
                        new MatcherNode.AnyMatcher(
                                "any-match", List.of(matchedMatcher, deferredMatcher)),
                        ruleEvaluationInput);
        RuleEvaluationResult notMatchedResult =
                ruleEvaluator.evaluate(
                        new MatcherNode.NotMatcher("not", matchedMatcher), ruleEvaluationInput);
        RuleEvaluationResult notDeferredResult =
                ruleEvaluator.evaluate(
                        new MatcherNode.NotMatcher("not-deferred", deferredMatcher),
                        ruleEvaluationInput);

        assertThat(allMatchedResult.status()).isEqualTo(MatcherEvaluationState.DEFERRED);
        assertThat(anyMatchedResult.status()).isEqualTo(MatcherEvaluationState.DEFERRED);
        assertThat(anyWithMatchResult.status()).isEqualTo(MatcherEvaluationState.MATCHED);
        assertThat(notMatchedResult.status()).isEqualTo(MatcherEvaluationState.NOT_MATCHED);
        assertThat(notDeferredResult.status()).isEqualTo(MatcherEvaluationState.DEFERRED);
        assertThat(allMatchedResult.evidenceById().keySet()).containsExactly("matched", "deferred");
    }

    @Test
    void semantic_intent_is_deferred_and_rule_evaluator_has_no_llm_gateway_dependency() {
        RuleEvaluationResult evaluationResult =
                ruleEvaluator.evaluate(
                        new SemanticIntentMatcher(
                                "semantic", "messages that need a thoughtful reply", true),
                        matchingInput());

        assertThat(evaluationResult.status()).isEqualTo(MatcherEvaluationState.DEFERRED);
        assertThat(evaluationResult.deferredEvidenceIds()).containsExactly("semantic");
        assertThat(
                        Arrays.stream(RuleEvaluator.class.getDeclaredFields())
                                .noneMatch(field -> field.getType().equals(LlmGateway.class)))
                .isTrue();
    }

    @Test
    void repeated_evaluation_is_stable_for_same_matcher_and_input() {
        MatcherNode matcherNode =
                new MatcherNode.AllMatcher(
                        "all",
                        List.of(
                                new MatcherNode.SenderDomainMatcher("sender-domain", "stripe.com"),
                                new MatcherNode.SubjectContainsMatcher("subject", "receipt"),
                                new MatcherNode.GmailLabelPresentMatcher("label", "INBOX")));

        RuleEvaluationResult firstResult = ruleEvaluator.evaluate(matcherNode, matchingInput());
        RuleEvaluationResult secondResult = ruleEvaluator.evaluate(matcherNode, matchingInput());

        assertThat(firstResult).isEqualTo(secondResult);
        assertThat(firstResult.evidenceById().keySet())
                .containsExactly("sender-domain", "subject", "label");
    }

    @Test
    void regex_evaluation_uses_re2j_and_not_java_pattern_for_user_authored_patterns()
            throws Exception {
        String source =
                Files.readString(
                        Path.of(
                                "src/main/java/com/zeromail/core/rules/service/RuleEvaluator.java"));

        assertThat(source).contains("com.google.re2j.Pattern");
        assertThat(source).doesNotContain("java.util.regex.Pattern");
    }

    @Test
    void matcher_body_evidence_contract_is_explicit_for_every_phase_3_matcher_type() {
        List<MatcherNode> matcherNodes =
                List.of(
                        new MatcherNode.SenderEmailMatcher("sender-email", "billing@stripe.com"),
                        new MatcherNode.SenderDomainMatcher("sender-domain", "stripe.com"),
                        new MatcherNode.RecipientToMatcher("recipient-to", "founder@example.test"),
                        new MatcherNode.RecipientCcMatcher("recipient-cc", "ops@example.test"),
                        new MatcherNode.SubjectContainsMatcher("subject-contains", "receipt"),
                        new MatcherNode.SubjectEqualsMatcher(
                                "subject-equals", "Receipt from Stripe"),
                        new MatcherNode.SubjectRegexMatcher("subject-regex", "(?i)receipt"),
                        new MatcherNode.GmailLabelPresentMatcher("label-present", "INBOX"),
                        new MatcherNode.GmailLabelAbsentMatcher("label-absent", "Label_999"),
                        new MatcherNode.GmailCategoryPresentMatcher(
                                "category-present", "promotions"),
                        new MatcherNode.GmailCategoryAbsentMatcher("category-absent", "social"),
                        new MatcherNode.HasAttachmentMatcher("has-attachment"),
                        new MatcherNode.ListUnsubscribePresentMatcher("list-unsubscribe"),
                        new MatcherNode.NewsletterIndicatorMatcher("newsletter"),
                        new MatcherNode.MessageAgeMatcher(
                                "age", MatcherNode.MessageAgeOperator.NEWER_THAN_DAYS, 7),
                        new MatcherNode.MessageDateMatcher(
                                "date",
                                MatcherNode.MessageDateOperator.ON,
                                LocalDate.parse("2026-05-08")),
                        new SemanticIntentMatcher(
                                "semantic", "messages that need a thoughtful reply", true));

        assertThat(matcherNodes)
                .allSatisfy(
                        matcherNode -> assertThat(matcherNode.requiresBodyEvidence()).isFalse());
        assertThat(new MatcherNode.AllMatcher("all", matcherNodes).requiresBodyEvidence())
                .isFalse();
        assertThat(new MatcherNode.AnyMatcher("any", matcherNodes).requiresBodyEvidence())
                .isFalse();
        assertThat(
                        new MatcherNode.NotMatcher("not", matcherNodes.getFirst())
                                .requiresBodyEvidence())
                .isFalse();
    }

    private void assertMatched(MatcherNode matcherNode) {
        assertThat(ruleEvaluator.evaluate(matcherNode, matchingInput()).status())
                .isEqualTo(MatcherEvaluationState.MATCHED);
    }

    private static RuleEvaluationInput matchingInput() {
        return new RuleEvaluationInput(
                "billing@stripe.com",
                "stripe.com",
                List.of("founder@example.test"),
                List.of("ops@example.test"),
                "Receipt from Stripe",
                List.of("INBOX", "Label_123"),
                List.of("promotions"),
                Instant.parse("2026-05-08T10:00:00Z"),
                Instant.parse("2026-05-09T10:00:00Z"),
                true,
                true,
                true,
                Optional.empty(),
                Set.of());
    }
}
