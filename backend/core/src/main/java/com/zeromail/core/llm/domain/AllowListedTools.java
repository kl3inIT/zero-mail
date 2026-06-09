package com.zeromail.core.llm.domain;

import com.zeromail.core.llm.usecases.LlmTool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AllowListedTools {

    private static final List<String> RULE_MATCHER_TYPE_IDS =
            List.of(
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
    private static final List<String> RULE_ACTION_TYPE_IDS =
            List.of(
                    "label",
                    "archive",
                    "save_draft",
                    "mark_read",
                    "star",
                    "add_to_digest",
                    "mark_spam",
                    "send_reply",
                    "forward_email",
                    "send_email");

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
                                "rules.v1 matcher tree using the locked matcher vocabulary. The matcher represents the email condition only. Boolean groups use ALL, ANY, or NOT and carry children. Leaf matchers carry their own payload field.",
                                "properties",
                                matcherProperties(),
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
                                        "additionalProperties",
                                        false,
                                        "properties",
                                        ruleActionIntentProperties(),
                                        "required",
                                        List.of("type"))),
                "clarificationRequired", Map.of("type", "boolean"),
                "clarificationQuestion",
                        Map.of("type", List.of("string", "null"), "maxLength", 240));
    }

    private static Map<String, Object> matcherProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
                "type",
                Map.of(
                        "type",
                        "string",
                        "enum",
                        RULE_MATCHER_TYPE_IDS,
                        "description",
                        "Matcher kind — exact UPPERCASE_UNDERSCORE id from rules.v1. Do NOT use AND/OR/&&/||."));
        properties.put(
                "intent",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "SEMANTIC_INTENT: concise description of the email meaning to recognize, in the user's language. Required when type=SEMANTIC_INTENT."));
        properties.put(
                "deferred",
                Map.of(
                        "type",
                        "boolean",
                        "description",
                        "SEMANTIC_INTENT: always true — match each email individually at triage time."));
        properties.put(
                "email",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "SENDER_EMAIL / RECIPIENT_TO / RECIPIENT_CC: full email address."));
        properties.put(
                "domain",
                Map.of("type", "string", "description", "SENDER_DOMAIN: domain name without @."));
        properties.put(
                "text",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "SUBJECT_CONTAINS / SUBJECT_EQUALS: subject text to match."));
        properties.put(
                "regexPattern",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "SUBJECT_REGEX: Java-compatible regex for the subject line."));
        properties.put(
                "labelId",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "GMAIL_LABEL_PRESENT / GMAIL_LABEL_ABSENT: Gmail label id or display name."));
        properties.put(
                "category",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "GMAIL_CATEGORY_PRESENT / GMAIL_CATEGORY_ABSENT: Gmail category id (e.g. CATEGORY_PROMOTIONS)."));
        properties.put(
                "minAgeDays",
                Map.of(
                        "type",
                        "integer",
                        "description",
                        "MESSAGE_AGE: minimum message age in days."));
        properties.put(
                "maxAgeDays",
                Map.of(
                        "type",
                        "integer",
                        "description",
                        "MESSAGE_AGE: maximum message age in days."));
        properties.put(
                "onOrAfter",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "MESSAGE_DATE: ISO yyyy-mm-dd lower date bound (inclusive)."));
        properties.put(
                "onOrBefore",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "MESSAGE_DATE: ISO yyyy-mm-dd upper date bound (inclusive)."));
        properties.put(
                "children",
                Map.of(
                        "type",
                        "array",
                        "description",
                        "ALL / ANY / NOT: array of nested matcher objects.",
                        "items",
                        Map.of("type", "object")));
        return Map.copyOf(properties);
    }

    private static Map<String, Object> ruleActionIntentProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", Map.of("type", "string", "enum", RULE_ACTION_TYPE_IDS));
        properties.put(
                "value",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "Legacy compatibility only; prefer explicit action fields."));
        properties.put(
                "labelName",
                Map.of("type", "string", "description", "Label text requested by the user."));
        properties.put(
                "body",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "User-owned draft/send body for save_draft, send_reply, or send_email. Never copy Gmail-read body content here."));
        properties.put(
                "instruction",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "Requested draft/reply/forward instruction."));
        properties.put(
                "recipients",
                Map.of(
                        "type",
                        "array",
                        "items",
                        Map.of("type", "string"),
                        "description",
                        "Forward recipients for forward_email."));
        properties.put(
                "to",
                Map.of(
                        "type",
                        "array",
                        "items",
                        Map.of("type", "string"),
                        "description",
                        "Primary recipients for send_email; also accepted as a forward_email recipient alias."));
        properties.put("cc", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("bcc", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put(
                "subject", Map.of("type", "string", "description", "Subject for send_email."));
        properties.put(
                "note",
                Map.of(
                        "type",
                        "string",
                        "description",
                        "Optional forward note; instruction is preferred."));
        return Map.copyOf(properties);
    }
}
