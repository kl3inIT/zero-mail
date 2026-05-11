package com.zeromail.core.rules.usecases;

import com.google.re2j.Pattern;
import com.zeromail.core.rules.domain.MatcherNode;
import com.zeromail.core.rules.domain.MatcherType;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class RuleAstJsonValidator {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 64;
    private static final int MAX_CHILDREN = 24;
    private static final int MAX_TEXT_LENGTH = 512;
    private static final int MAX_REGEX_LENGTH = 256;
    private static final Set<String> COMMON_FIELDS = Set.of("type", "matcherType", "nodeId");

    public void validateMatcherJson(String matcherJson) {
        JsonNode matcherRoot = readJson(matcherJson);
        ValidationState validationState = new ValidationState();
        validateSchemaVersion(matcherRoot);
        validateMatcherNode(matcherRoot, true, 0, validationState);
    }

    private static JsonNode readJson(String matcherJson) {
        if (matcherJson == null || matcherJson.isBlank()) {
            throw new IllegalArgumentException("matcherJson must not be blank");
        }
        try {
            return OBJECT_MAPPER.readTree(matcherJson);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException("matcherJson must be valid JSON", jacksonException);
        }
    }

    private static void validateSchemaVersion(JsonNode matcherRoot) {
        String schemaVersion = textValue(matcherRoot, "schemaVersion", "schema_version");
        RuleSchemaVersion.fromId(schemaVersion);
    }

    private static void validateMatcherNode(
            JsonNode matcherNode, boolean rootMatcher, int depth, ValidationState validationState) {
        if (!matcherNode.isObject()) {
            throw new IllegalArgumentException("matcher node must be an object");
        }
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("matcher tree is too deep");
        }
        validationState.visitNode();

        String matcherTypeId = textValue(matcherNode, "type", "matcherType");
        MatcherType matcherType = MatcherType.fromId(matcherTypeId);
        validateOptionalText(matcherNode, "nodeId", MAX_TEXT_LENGTH);

        switch (matcherType) {
            case SENDER_EMAIL, RECIPIENT_TO, RECIPIENT_CC -> {
                rejectUnknownFields(matcherNode, rootMatcher, Set.of("email"));
                requiredText(matcherNode, "email", MAX_TEXT_LENGTH);
            }
            case SENDER_DOMAIN -> {
                rejectUnknownFields(matcherNode, rootMatcher, Set.of("domain"));
                requiredText(matcherNode, "domain", MAX_TEXT_LENGTH);
            }
            case SUBJECT_CONTAINS, SUBJECT_EQUALS -> {
                rejectUnknownFields(matcherNode, rootMatcher, Set.of("text", "value"));
                requiredFirstText(matcherNode, MAX_TEXT_LENGTH, "text", "value");
            }
            case SUBJECT_REGEX -> {
                rejectUnknownFields(
                        matcherNode, rootMatcher, Set.of("regexPattern", "regex", "text"));
                String regexPattern =
                        requiredFirstText(
                                matcherNode, MAX_REGEX_LENGTH, "regexPattern", "regex", "text");
                Pattern.compile(regexPattern);
            }
            case GMAIL_LABEL_PRESENT, GMAIL_LABEL_ABSENT -> {
                rejectUnknownFields(matcherNode, rootMatcher, Set.of("labelId", "label"));
                requiredFirstText(matcherNode, MAX_TEXT_LENGTH, "labelId", "label");
            }
            case GMAIL_CATEGORY_PRESENT, GMAIL_CATEGORY_ABSENT -> {
                rejectUnknownFields(matcherNode, rootMatcher, Set.of("category"));
                requiredText(matcherNode, "category", MAX_TEXT_LENGTH);
            }
            case HAS_ATTACHMENT, LIST_UNSUBSCRIBE_PRESENT, NEWSLETTER_INDICATOR ->
                    rejectUnknownFields(matcherNode, rootMatcher, Set.of());
            case MESSAGE_AGE -> {
                rejectUnknownFields(matcherNode, rootMatcher, Set.of("operator", "days"));
                MatcherNode.MessageAgeOperator.valueOf(
                        requiredText(matcherNode, "operator", MAX_TEXT_LENGTH));
                JsonNode daysNode = required(matcherNode, "days");
                if (!daysNode.isIntegralNumber()
                        || !daysNode.canConvertToInt()
                        || daysNode.intValue() < 0) {
                    throw new IllegalArgumentException("days must be a non-negative integer");
                }
            }
            case MESSAGE_DATE -> {
                rejectUnknownFields(matcherNode, rootMatcher, Set.of("operator", "date"));
                MatcherNode.MessageDateOperator.valueOf(
                        requiredText(matcherNode, "operator", MAX_TEXT_LENGTH));
                LocalDate.parse(requiredText(matcherNode, "date", MAX_TEXT_LENGTH));
            }
            case ALL, ANY -> {
                rejectUnknownFields(matcherNode, rootMatcher, Set.of("children"));
                JsonNode childrenNode = required(matcherNode, "children");
                if (!childrenNode.isArray()
                        || childrenNode.isEmpty()
                        || childrenNode.size() > MAX_CHILDREN) {
                    throw new IllegalArgumentException(
                            matcherType.id() + " children must be a bounded non-empty array");
                }
                for (JsonNode childNode : childrenNode) {
                    validateMatcherNode(childNode, false, depth + 1, validationState);
                }
            }
            case NOT -> {
                rejectUnknownFields(matcherNode, rootMatcher, Set.of("child"));
                validateMatcherNode(
                        required(matcherNode, "child"), false, depth + 1, validationState);
            }
            case SEMANTIC_INTENT -> {
                rejectUnknownFields(
                        matcherNode, rootMatcher, Set.of("intent", "description", "deferred"));
                requiredFirstText(matcherNode, MAX_TEXT_LENGTH, "intent", "description");
                JsonNode deferredNode = required(matcherNode, "deferred");
                if (!deferredNode.isBoolean() || !deferredNode.booleanValue()) {
                    throw new IllegalArgumentException("SEMANTIC_INTENT matcher must be deferred");
                }
            }
        }
    }

    private static void rejectUnknownFields(
            JsonNode matcherNode, boolean rootMatcher, Set<String> matcherSpecificFields) {
        Set<String> allowedFields = new LinkedHashSet<>(COMMON_FIELDS);
        allowedFields.addAll(matcherSpecificFields);
        if (rootMatcher) {
            allowedFields.add("schemaVersion");
            allowedFields.add("schema_version");
        }
        for (var property : matcherNode.properties()) {
            String fieldName = property.getKey();
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("unknown matcher field");
            }
        }
    }

    private static JsonNode required(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return fieldNode;
    }

    private static String textValue(
            JsonNode jsonNode, String primaryFieldName, String fallbackFieldName) {
        JsonNode primaryNode = jsonNode.path(primaryFieldName);
        JsonNode selectedNode =
                primaryNode.isMissingNode() || primaryNode.isNull()
                        ? jsonNode.path(fallbackFieldName)
                        : primaryNode;
        if (selectedNode.isMissingNode() || selectedNode.isNull() || !selectedNode.isString()) {
            throw new IllegalArgumentException(primaryFieldName + " is required");
        }
        return Objects.requireNonNull(selectedNode.asString());
    }

    private static String requiredFirstText(
            JsonNode jsonNode, int maxLength, String firstFieldName, String... otherFieldNames) {
        String value = optionalText(jsonNode, firstFieldName, maxLength);
        for (String fieldName : otherFieldNames) {
            if (value == null) {
                value = optionalText(jsonNode, fieldName, maxLength);
            }
        }
        if (value == null) {
            throw new IllegalArgumentException(firstFieldName + " is required");
        }
        return value;
    }

    private static String requiredText(JsonNode jsonNode, String fieldName, int maxLength) {
        String value = optionalText(jsonNode, fieldName, maxLength);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static void validateOptionalText(JsonNode jsonNode, String fieldName, int maxLength) {
        optionalText(jsonNode, fieldName, maxLength);
    }

    private static String optionalText(JsonNode jsonNode, String fieldName, int maxLength) {
        JsonNode fieldNode = jsonNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        if (!fieldNode.isString()) {
            throw new IllegalArgumentException(fieldName + " must be a string");
        }
        String value = fieldNode.asString().trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return value;
    }

    private static final class ValidationState {

        private int visitedNodes;

        void visitNode() {
            visitedNodes++;
            if (visitedNodes > MAX_NODES) {
                throw new IllegalArgumentException("matcher tree has too many nodes");
            }
        }
    }
}
