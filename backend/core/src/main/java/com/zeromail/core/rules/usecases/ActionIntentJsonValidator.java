package com.zeromail.core.rules.usecases;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.rules.exception.RuleValidationException;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ActionIntentJsonValidator {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final int MAX_ACTION_TEXT_LENGTH = 500;
    private static final Set<String> COMMON_FIELDS = Set.of("type", "action");

    public void validateActionIntentsJson(String actionIntentsJson) {
        JsonNode actionIntentRoot = readJson(actionIntentsJson);
        if (!actionIntentRoot.isArray() || actionIntentRoot.isEmpty()) {
            throw new IllegalArgumentException("actionIntentsJson must be a non-empty JSON array");
        }
        for (JsonNode actionIntentNode : actionIntentRoot) {
            if (!actionIntentNode.isObject()) {
                throw new IllegalArgumentException("action intent must be an object");
            }
            RuleActionType actionType;
            try {
                actionType = RuleActionType.fromId(actionType(actionIntentNode));
            } catch (NoSuchElementException unsafeActionFailure) {
                throw RuleValidationException.unsafeAction();
            }
            switch (actionType) {
                case LABEL -> {
                    rejectUnknownFields(actionIntentNode, Set.of("labelName"));
                    requiredText(actionIntentNode, "labelName", MAX_ACTION_TEXT_LENGTH);
                }
                case ARCHIVE -> rejectUnknownFields(actionIntentNode, Set.of());
                case SAVE_DRAFT -> {
                    rejectUnknownFields(actionIntentNode, Set.of("instruction", "body", "value"));
                    requiredFirstText(
                            actionIntentNode,
                            MAX_ACTION_TEXT_LENGTH,
                            "instruction",
                            "body",
                            "value");
                }
            }
        }
    }

    private static JsonNode readJson(String actionIntentsJson) {
        if (actionIntentsJson == null || actionIntentsJson.isBlank()) {
            throw new IllegalArgumentException("actionIntentsJson must not be blank");
        }
        try {
            return OBJECT_MAPPER.readTree(actionIntentsJson);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException(
                    "actionIntentsJson must be valid JSON", jacksonException);
        }
    }

    private static String actionType(JsonNode actionIntentNode) {
        JsonNode typeNode = actionIntentNode.path("type");
        JsonNode selectedNode =
                typeNode.isMissingNode() || typeNode.isNull()
                        ? actionIntentNode.path("action")
                        : typeNode;
        if (selectedNode.isMissingNode() || selectedNode.isNull() || !selectedNode.isString()) {
            throw new IllegalArgumentException("action intent type is required");
        }
        return selectedNode.asString();
    }

    private static void rejectUnknownFields(
            JsonNode actionIntentNode, Set<String> actionSpecificFields) {
        Set<String> allowedFields = new LinkedHashSet<>(COMMON_FIELDS);
        allowedFields.addAll(actionSpecificFields);
        for (var property : actionIntentNode.properties()) {
            String fieldName = property.getKey();
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("unknown action intent field");
            }
        }
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
}
