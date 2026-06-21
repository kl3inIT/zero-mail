package com.zeromail.core.rules.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.inbox.domain.MessageClass;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Phase 12 W5 (CAL-TRIAGE-03, D-09): the seeded {@code system-calendar(-vi)} rule must match
 * structurally — terminal MATCHED when the W4 {@code CalendarMessageClassifier} wrote a {@code
 * message_class} on the inbox projection, terminal NOT_MATCHED otherwise. No LLM call, no DEFERRED
 * state, no backend downgrade or audit reason. User-authored SEMANTIC_INTENT rules retain full
 * authority — the preset matcher does not override them.
 */
class RuleEvaluatorCalendarPresetTest {

    private static final String CALENDAR_NODE_ID = "system-calendar";

    private final RuleEvaluator ruleEvaluator = new RuleEvaluator();

    @Test
    void presetCalendarMatcher_matchesWhenMessageClassIsPresent() {
        RuleEvaluationInput ruleEvaluationInput = inputWithMessageClass(MessageClass.INVITE);
        MatcherNode.PresetCalendarMatcher presetCalendarMatcher =
                new MatcherNode.PresetCalendarMatcher(CALENDAR_NODE_ID);

        RuleEvaluationResult ruleEvaluationResult =
                ruleEvaluator.evaluate(presetCalendarMatcher, ruleEvaluationInput);

        assertThat(ruleEvaluationResult.status()).isEqualTo(MatcherEvaluationState.MATCHED);
        assertThat(ruleEvaluationResult.matchedEvidenceIds()).containsExactly(CALENDAR_NODE_ID);
        assertThat(ruleEvaluationResult.evidenceById())
                .containsEntry(CALENDAR_NODE_ID, "preset_calendar");
    }

    @Test
    void presetCalendarMatcher_doesNotMatchWhenMessageClassIsAbsent() {
        RuleEvaluationInput ruleEvaluationInput = inputWithoutMessageClass();
        MatcherNode.PresetCalendarMatcher presetCalendarMatcher =
                new MatcherNode.PresetCalendarMatcher(CALENDAR_NODE_ID);

        RuleEvaluationResult ruleEvaluationResult =
                ruleEvaluator.evaluate(presetCalendarMatcher, ruleEvaluationInput);

        assertThat(ruleEvaluationResult.status()).isEqualTo(MatcherEvaluationState.NOT_MATCHED);
        assertThat(ruleEvaluationResult.matchedEvidenceIds()).isEmpty();
        assertThat(ruleEvaluationResult.evidenceById())
                .containsEntry(CALENDAR_NODE_ID, "preset_calendar");
    }

    @ParameterizedTest
    @EnumSource(MessageClass.class)
    void presetCalendarMatcher_matchesForEveryMessageClassEnumValue(MessageClass messageClass) {
        RuleEvaluationInput ruleEvaluationInput = inputWithMessageClass(messageClass);
        MatcherNode.PresetCalendarMatcher presetCalendarMatcher =
                new MatcherNode.PresetCalendarMatcher(CALENDAR_NODE_ID);

        RuleEvaluationResult ruleEvaluationResult =
                ruleEvaluator.evaluate(presetCalendarMatcher, ruleEvaluationInput);

        assertThat(ruleEvaluationResult.status())
                .as(
                        "PRESET_CALENDAR must match for every MessageClass value (W4 only writes"
                                + " INVITE/CANCEL/RSVP today, but the matcher is structurally"
                                + " present-or-absent — RESCHEDULE will be added without code"
                                + " change in v1.5)")
                .isEqualTo(MatcherEvaluationState.MATCHED);
    }

    @Test
    void semanticIntentMatcher_unchangedByPresetSibling() {
        // A user-authored SEMANTIC_INTENT matcher must still defer (no override-by-PRESET).
        // D-09 invariant: user rules retain full action authority.
        RuleEvaluationInput ruleEvaluationInput = inputWithMessageClass(MessageClass.INVITE);
        SemanticIntentMatcher userSemanticMatcher =
                new SemanticIntentMatcher(
                        "user-rule-node", "messages about scheduling meetings with my CTO", true);

        RuleEvaluationResult ruleEvaluationResult =
                ruleEvaluator.evaluate(userSemanticMatcher, ruleEvaluationInput);

        assertThat(ruleEvaluationResult.status())
                .as(
                        "user-authored SEMANTIC_INTENT keeps deferring — the W5 PRESET matcher"
                                + " does NOT bypass or override sibling user rules")
                .isEqualTo(MatcherEvaluationState.DEFERRED);
    }

    @Test
    void presetCalendarMatcher_doesNotEmitAuditReason() {
        // Regression guard: D-09 explicitly rejected a CalendarAwareGuard / downgrade-with-audit
        // path. The PRESET result must surface only the structural diagnostic; no auxiliary
        // override / audit-reason field exists on RuleEvaluationResult, and no future change
        // should add one for this matcher type.
        RuleEvaluationInput ruleEvaluationInput = inputWithMessageClass(MessageClass.CANCEL);
        MatcherNode.PresetCalendarMatcher presetCalendarMatcher =
                new MatcherNode.PresetCalendarMatcher(CALENDAR_NODE_ID);

        RuleEvaluationResult ruleEvaluationResult =
                ruleEvaluator.evaluate(presetCalendarMatcher, ruleEvaluationInput);

        assertThat(ruleEvaluationResult.evidenceById())
                .containsExactly(java.util.Map.entry(CALENDAR_NODE_ID, "preset_calendar"));
        assertThat(ruleEvaluationResult.status()).isEqualTo(MatcherEvaluationState.MATCHED);
        assertThat(ruleEvaluationResult.deferredEvidenceIds()).isEmpty();
    }

    private static RuleEvaluationInput inputWithMessageClass(MessageClass messageClass) {
        return new RuleEvaluationInput(
                "scheduler@example.com",
                "example.com",
                List.of("founder@zeromail.test"),
                List.of(),
                "Invitation: Q3 planning sync",
                List.of("INBOX"),
                List.of(),
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-21T10:00:00Z"),
                false,
                false,
                false,
                false,
                Optional.empty(),
                Set.of(),
                Optional.of(messageClass));
    }

    private static RuleEvaluationInput inputWithoutMessageClass() {
        return new RuleEvaluationInput(
                "newsletter@example.com",
                "example.com",
                List.of("founder@zeromail.test"),
                List.of(),
                "Weekly digest",
                List.of("INBOX"),
                List.of(),
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-21T10:00:00Z"),
                false,
                false,
                true,
                false,
                Optional.empty(),
                Set.of());
    }
}
