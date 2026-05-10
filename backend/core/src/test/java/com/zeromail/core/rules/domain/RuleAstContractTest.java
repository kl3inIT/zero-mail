package com.zeromail.core.rules.domain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class RuleAstContractTest {

  @Test
  void matcher_type_ids_cover_the_locked_phase_3_vocabulary() throws Exception {
    Class<?> matcherTypeClass = Class.forName("com.zeromail.core.rules.domain.MatcherType");
    Method valuesMethod = matcherTypeClass.getMethod("values");
    Object[] matcherTypes = (Object[]) valuesMethod.invoke(null);

    List<String> matcherIds =
        Stream.of(matcherTypes)
            .map(RuleAstContractTest::invokeId)
            .toList();

    assertThat(matcherIds)
        .containsExactlyInAnyOrder(
            "SENDER_EMAIL",
            "SENDER_DOMAIN",
            "RECIPIENT_TO",
            "RECIPIENT_CC",
            "SUBJECT_CONTAINS",
            "SUBJECT_EQUALS",
            "SUBJECT_REGEX",
            "GMAIL_LABEL_PRESENT",
            "GMAIL_LABEL_ABSENT",
            "GMAIL_CATEGORY_PRESENT",
            "GMAIL_CATEGORY_ABSENT",
            "HAS_ATTACHMENT",
            "LIST_UNSUBSCRIBE_PRESENT",
            "NEWSLETTER_INDICATOR",
            "MESSAGE_AGE",
            "MESSAGE_DATE",
            "ALL",
            "ANY",
            "NOT",
            "SEMANTIC_INTENT");
  }

  @Test
  void safe_action_type_ids_match_the_existing_llm_action_allow_list() throws Exception {
    Class<?> ruleActionTypeClass = Class.forName("com.zeromail.core.rules.domain.RuleActionType");
    Method valuesMethod = ruleActionTypeClass.getMethod("values");
    Object[] ruleActionTypes = (Object[]) valuesMethod.invoke(null);

    List<String> ruleActionIds =
        Stream.of(ruleActionTypes)
            .map(RuleAstContractTest::invokeId)
            .toList();

    assertThat(ruleActionIds).containsExactlyInAnyOrder("label", "archive", "save_draft");
    assertThat(ruleActionIds).doesNotContain("send", "forward", "spam", "webhook");
  }

  @Test
  void semantic_intent_nodes_are_constructed_as_deferred_only() throws Exception {
    Class<?> semanticIntentMatcherClass =
        Class.forName("com.zeromail.core.rules.domain.SemanticIntentMatcher");
    Constructor<?> constructor =
        semanticIntentMatcherClass.getConstructor(String.class, String.class, boolean.class);

    Object deferredMatcher =
        constructor.newInstance("semantic-1", "messages that need a thoughtful reply", true);
    Method deferredMethod = semanticIntentMatcherClass.getMethod("deferred");

    assertThat(deferredMethod.invoke(deferredMatcher)).isEqualTo(true);
    assertThatThrownBy(
            () -> constructor.newInstance("semantic-2", "messages that sound urgent", false))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unknown_matcher_nodes_are_rejected_before_persistence() throws Exception {
    Class<?> validatorClass = Class.forName("com.zeromail.core.rules.domain.RuleAstJsonValidator");
    Object validator = validatorClass.getConstructor().newInstance();
    Method validateMethod = validatorClass.getMethod("validateMatcherJson", String.class);

    assertThatThrownBy(
            () ->
                validateMethod.invoke(
                    validator,
                    """
                    {"schemaVersion":"rules.v1","type":"SEND_EVERYTHING","children":[]}
                    """))
        .hasRootCauseInstanceOf(NoSuchElementException.class);
  }

  private static String invokeId(Object enumValue) {
    try {
      return (String) enumValue.getClass().getMethod("id").invoke(enumValue);
    } catch (ReflectiveOperationException reflectionFailure) {
      throw new AssertionError("Matcher/action enum values must expose id()", reflectionFailure);
    }
  }
}
