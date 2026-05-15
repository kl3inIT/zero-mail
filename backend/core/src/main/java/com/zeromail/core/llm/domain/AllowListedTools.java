package com.zeromail.core.llm.domain;

import com.zeromail.core.llm.usecases.LlmTool;
import com.zeromail.core.rules.domain.MatcherType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AllowListedTools {

    private static final List<LlmTool> ALLOW_LISTED =
            List.of(
                    new LlmTool(
                            "label",
                            "Apply a Gmail label to the email",
                            Map.of(
                                    "type", "object",
                                    "properties",
                                            Map.of(
                                                    "value",
                                                    Map.of(
                                                            "type",
                                                            "string",
                                                            "description",
                                                            "Label name")),
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
                                            Map.of(
                                                    "body",
                                                    Map.of(
                                                            "type",
                                                            "string",
                                                            "description",
                                                            "Draft body")),
                                    "required", List.of("body"))));

    private static final List<LlmTool> RULE_COMPILE =
            List.of(
                    new LlmTool(
                            "rule_compile",
                            "Compile a natural-language rule into the rules.v1 matcher and action schema",
                            ruleCompileSchema()));
    private static final List<LlmTool> RULE_COMPILE_REVIEW_DRAFT =
            List.of(
                    new LlmTool(
                            "rule_compile",
                            "Compile a natural-language rule into an editable rules.v1 review-form draft",
                            ruleCompileReviewDraftSchema()));
    private static final List<LlmTool> SAVE_DRAFT_ONLY =
            ALLOW_LISTED.stream().filter(tool -> "save_draft".equals(tool.name())).toList();

    public List<LlmTool> tools() {
        return tools(LlmToolProfile.SAFE_ACTIONS);
    }

    public List<LlmTool> tools(LlmToolProfile toolProfile) {
        return switch (toolProfile) {
            case SAFE_ACTIONS -> ALLOW_LISTED;
            case RULE_COMPILE -> RULE_COMPILE;
            case RULE_COMPILE_REVIEW_DRAFT -> RULE_COMPILE_REVIEW_DRAFT;
            case SAVE_DRAFT_ONLY -> SAVE_DRAFT_ONLY;
        };
    }

    private static Map<String, Object> ruleCompileSchema() {
        return Map.of(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                ruleCompileProperties(),
                "required",
                List.of(
                        "schemaVersion",
                        "sourceLanguage",
                        "displayName",
                        "matcher",
                        "actionIntents",
                        "clarificationRequired"));
    }

    private static Map<String, Object> ruleCompileReviewDraftSchema() {
        Map<String, Object> properties = new LinkedHashMap<>(ruleCompileProperties());
        properties.put("clarificationRequired", Map.of("const", false));
        properties.remove("clarificationQuestion");
        return Map.of(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                Map.copyOf(properties),
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
                "displayName",
                        Map.of(
                                "type",
                                "string",
                                "minLength",
                                1,
                                "maxLength",
                                80,
                                "description",
                                "Short form-ready rule title extracted from the user's meaning."),
                "matcher",
                        Map.of(
                                "type",
                                "object",
                                "description",
                                "rules.v1 matcher tree using the locked matcher vocabulary. The matcher represents the email condition only. Boolean groups use ALL, ANY, or NOT and carry children. Leaf matchers carry their own value field.",
                                "properties",
                                Map.of(
                                        "type",
                                        Map.of(
                                                "type",
                                                "string",
                                                "enum",
                                                Arrays.stream(MatcherType.values())
                                                        .map(MatcherType::id)
                                                        .toList(),
                                                "description",
                                                "Matcher kind — exact UPPERCASE_UNDERSCORE id from rules.v1. Do NOT use AND/OR/&&/||.")),
                                "required",
                                List.of("type")),
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
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "Legacy action value; for label actions this is the label text."),
                                                "labelName",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "Label text requested by the user."),
                                                "body",
                                                Map.of("type", "string"),
                                                "instruction",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "Requested draft instruction for save_draft actions.")),
                                        "required",
                                        List.of("type"))),
                "clarificationRequired", Map.of("type", "boolean"),
                "clarificationQuestion",
                        Map.of("type", List.of("string", "null"), "maxLength", 240));
    }
}
