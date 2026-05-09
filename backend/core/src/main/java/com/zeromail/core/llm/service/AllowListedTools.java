package com.zeromail.core.llm.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.zeromail.core.llm.model.LlmTool;
import com.zeromail.core.llm.model.LlmToolProfile;

@Component
public class AllowListedTools {

  private static final List<LlmTool> ALLOW_LISTED =
      List.of(
          new LlmTool(
              "label",
              "Apply a Gmail label to the email",
              Map.of(
                  "type", "object",
                  "properties",
                      Map.of("value", Map.of("type", "string", "description", "Label name")),
                  "required", List.of("value"))),
          new LlmTool(
              "archive",
              "Archive the email (skip inbox)",
              Map.of("type", "object", "properties", Map.of())),
          new LlmTool(
              "save_draft",
              "Save a draft reply for the email",
              Map.of(
                  "type", "object",
                  "properties",
                      Map.of("body", Map.of("type", "string", "description", "Draft body")),
                  "required", List.of("body"))));

  private static final List<LlmTool> RULE_COMPILE =
      List.of(
          new LlmTool(
              "rule_compile",
              "Compile a natural-language rule into the rules.v1 matcher and action schema",
              ruleCompileSchema()));

  public List<LlmTool> tools() {
    return tools(LlmToolProfile.SAFE_ACTIONS);
  }

  public List<LlmTool> tools(LlmToolProfile toolProfile) {
    return switch (toolProfile) {
      case SAFE_ACTIONS -> ALLOW_LISTED;
      case RULE_COMPILE -> RULE_COMPILE;
    };
  }

  private static Map<String, Object> ruleCompileSchema() {
    return Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", ruleCompileProperties(),
        "required",
            List.of(
                "schemaVersion",
                "sourceLanguage",
                "displayName",
                "matcher",
                "actionIntents",
                "clarificationRequired"));
  }

  private static Map<String, Object> ruleCompileProperties() {
    return Map.of(
        "schemaVersion", Map.of("const", "rules.v1"),
        "sourceLanguage", Map.of("type", "string", "enum", List.of("en", "vi", "unknown")),
        "displayName", Map.of("type", "string", "minLength", 1, "maxLength", 80),
        "matcher",
            Map.of(
                "type",
                "object",
                "description",
                "rules.v1 matcher tree using the locked Phase 3 matcher vocabulary"),
        "actionIntents",
            Map.of(
                "type",
                "array",
                "items",
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "type",
                        Map.of(
                            "type",
                            "string",
                            "enum",
                            List.of("label", "archive", "save_draft")),
                        "value",
                        Map.of("type", "string"),
                        "body",
                        Map.of("type", "string")),
                    "required",
                    List.of("type"))),
        "clarificationRequired", Map.of("type", "boolean"),
        "clarificationQuestion", Map.of("type", List.of("string", "null"), "maxLength", 240));
  }
}
