package com.zeromail.core.rules.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class RuleModelTest {

  @Test
  void unknown_enum_ids_fail_loud() {
    assertThatThrownBy(() -> RuleLanguage.fromId("fr"))
        .isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> RuleSchemaVersion.fromId("rules.v2"))
        .isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> MatcherType.fromId("SEND_EVERYTHING"))
        .isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> RuleActionType.fromId("send"))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void semantic_intent_matcher_is_deferred_only_and_metadata_only_for_phase_3() {
    SemanticIntentMatcher matcher =
        new SemanticIntentMatcher("semantic-1", "messages that need a thoughtful reply", true);

    assertThat(matcher.deferred()).isTrue();
    assertThat(matcher.requiresBodyEvidence()).isFalse();
    assertThatThrownBy(
            () ->
                new SemanticIntentMatcher(
                    "semantic-2", "messages that sound urgent or frustrated", false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void schema_version_validation_rejects_unknown_matcher_ast_versions() {
    RuleAstJsonValidator validator = new RuleAstJsonValidator();

    assertThatThrownBy(
            () ->
                validator.validateMatcherJson(
                    """
                    {"schemaVersion":"rules.v2","type":"SENDER_DOMAIN","domain":"stripe.com"}
                    """))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void subject_regex_matcher_uses_re2j_validation() {
    MatcherNode.SubjectRegexMatcher validMatcher =
        new MatcherNode.SubjectRegexMatcher("subject-regex-1", "(?i).*receipt.*");

    assertThat(validMatcher.type()).isEqualTo(MatcherType.SUBJECT_REGEX);
    assertThat(validMatcher.requiresBodyEvidence()).isFalse();
    assertThatThrownBy(() -> new MatcherNode.SubjectRegexMatcher("subject-regex-2", "(.)\\1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void boolean_matchers_roll_up_body_evidence_contracts() {
    MatcherNode.SenderDomainMatcher senderDomainMatcher =
        new MatcherNode.SenderDomainMatcher("sender-domain-1", "stripe.com");
    MatcherNode.MessageDateMatcher messageDateMatcher =
        new MatcherNode.MessageDateMatcher(
            "message-date-1", MatcherNode.MessageDateOperator.AFTER, LocalDate.parse("2026-01-01"));
    MatcherNode.AllMatcher allMatcher =
        new MatcherNode.AllMatcher("all-1", List.of(senderDomainMatcher, messageDateMatcher));

    assertThat(allMatcher.requiresBodyEvidence()).isFalse();
  }

  @Test
  void action_intents_are_aligned_to_existing_llm_action_allow_list() {
    assertThat(ActionIntent.fromAction(com.zeromail.core.llm.model.Action.ARCHIVE))
        .isInstanceOf(ActionIntent.Archive.class);
    assertThat(RuleActionType.LABEL.llmAction())
        .isEqualTo(com.zeromail.core.llm.model.Action.LABEL);
  }
}
