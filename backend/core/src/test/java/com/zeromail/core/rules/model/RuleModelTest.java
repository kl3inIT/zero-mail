package com.zeromail.core.rules.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
  void matcher_json_validation_rejects_missing_leaf_fields_and_unknown_fields() {
    RuleAstJsonValidator validator = new RuleAstJsonValidator();

    assertThatThrownBy(
            () ->
                validator.validateMatcherJson(
                    """
                    {"schemaVersion":"rules.v1","type":"SENDER_DOMAIN"}
                    """))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                validator.validateMatcherJson(
                    """
                    {
                      "schemaVersion":"rules.v1",
                      "type":"SENDER_DOMAIN",
                      "domain":"stripe.com",
                      "prompt":"hidden"
                    }
                    """))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void matcher_json_validation_accepts_zero_field_matchers_but_bounds_tree_depth() {
    RuleAstJsonValidator validator = new RuleAstJsonValidator();

    assertThatCode(
            () ->
                validator.validateMatcherJson(
                    """
                    {"schemaVersion":"rules.v1","type":"HAS_ATTACHMENT"}
                    """))
        .doesNotThrowAnyException();

    String nestedMatcherNode = "{\"type\":\"HAS_ATTACHMENT\"}";
    for (int nestingLevel = 0; nestingLevel < 9; nestingLevel++) {
      nestedMatcherNode = "{\"type\":\"NOT\",\"child\":" + nestedMatcherNode + "}";
    }
    String deeplyNestedMatcher = "{\"schemaVersion\":\"rules.v1\"," + nestedMatcherNode.substring(1);
    assertThatThrownBy(() -> validator.validateMatcherJson(deeplyNestedMatcher))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void action_intent_json_validation_rejects_missing_required_fields_and_unknown_fields() {
    ActionIntentJsonValidator validator = new ActionIntentJsonValidator();

    assertThatThrownBy(() -> validator.validateActionIntentsJson("[{\"type\":\"label\"}]"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                validator.validateActionIntentsJson(
                    """
                    [
                      {
                        "type":"save_draft",
                        "instruction":"Draft a reply",
                        "prompt":"hidden"
                      }
                    ]
                    """))
        .isInstanceOf(IllegalArgumentException.class);
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
