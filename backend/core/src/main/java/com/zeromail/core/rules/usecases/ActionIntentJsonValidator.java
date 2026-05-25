package com.zeromail.core.rules.usecases;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.rules.exception.RuleValidationException;
import com.zeromail.core.shared.validation.EmailRecipientValidator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ActionIntentJsonValidator {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final int MAX_ACTION_TEXT_LENGTH = 500;
    private static final int MAX_ACTION_BODY_LENGTH = 4000;
    private static final Set<String> COMMON_FIELDS = Set.of("type", "action");
    private static final Set<String> GMAIL_READ_CONTENT_SOURCE_FIELDS =
            Set.of(
                    "gmailReadBody",
                    "gmailReadSnippet",
                    "rawEmailBody",
                    "rawMessageBody",
                    "emailBody",
                    "messageBody",
                    "threadBody",
                    "snippet");

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
                case ARCHIVE, MARK_READ, STAR, ADD_TO_DIGEST, MARK_SPAM ->
                        rejectUnknownFields(actionIntentNode, Set.of());
                case SAVE_DRAFT -> {
                    rejectUnknownFields(actionIntentNode, Set.of("instruction", "body", "value"));
                    requiredFirstText(
                            actionIntentNode,
                            MAX_ACTION_TEXT_LENGTH,
                            "instruction",
                            "body",
                            "value");
                }
                case SEND_REPLY -> {
                    rejectUnknownFields(actionIntentNode, Set.of("instruction", "body", "value"));
                    requiredFirstText(
                            actionIntentNode,
                            MAX_ACTION_TEXT_LENGTH,
                            "instruction",
                            "body",
                            "value");
                }
                case FORWARD_EMAIL -> {
                    rejectUnknownFields(
                            actionIntentNode, Set.of("recipients", "to", "instruction", "note"));
                    requiredRecipients(actionIntentNode, "recipients", "to");
                    optionalText(actionIntentNode, "instruction", MAX_ACTION_TEXT_LENGTH);
                    optionalText(actionIntentNode, "note", MAX_ACTION_TEXT_LENGTH);
                }
                case SEND_EMAIL -> {
                    rejectUnknownFields(
                            actionIntentNode,
                            Set.of("to", "recipients", "cc", "bcc", "subject", "body"));
                    requiredRecipients(actionIntentNode, "to", "recipients");
                    optionalRecipients(actionIntentNode, "cc");
                    optionalRecipients(actionIntentNode, "bcc");
                    requiredText(actionIntentNode, "subject", MAX_ACTION_TEXT_LENGTH);
                    requiredText(actionIntentNode, "body", MAX_ACTION_BODY_LENGTH);
                }
                default ->
                        throw new IllegalStateException(
                                "Unhandled rule action type: " + actionType);
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
            if (GMAIL_READ_CONTENT_SOURCE_FIELDS.contains(fieldName)) {
                throw new IllegalArgumentException("gmail-read content source field is forbidden");
            }
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

    private static List<String> requiredRecipients(
            JsonNode actionIntentNode, String primaryFieldName, String fallbackFieldName) {
        List<String> recipients =
                recipients(actionIntentNode, primaryFieldName, fallbackFieldName, true);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException(primaryFieldName + " is required");
        }
        return recipients;
    }

    private static List<String> optionalRecipients(JsonNode actionIntentNode, String fieldName) {
        return recipients(actionIntentNode, fieldName, fieldName, false);
    }

    private static List<String> recipients(
            JsonNode actionIntentNode,
            String primaryFieldName,
            String fallbackFieldName,
            boolean required) {
        JsonNode recipientNode = actionIntentNode.path(primaryFieldName);
        if ((recipientNode.isMissingNode() || recipientNode.isNull())
                && !primaryFieldName.equals(fallbackFieldName)) {
            recipientNode = actionIntentNode.path(fallbackFieldName);
        }
        if (recipientNode.isMissingNode() || recipientNode.isNull()) {
            if (required) {
                throw new IllegalArgumentException(primaryFieldName + " is required");
            }
            return List.of();
        }
        if (recipientNode.isString()) {
            return validateRecipients(
                    List.of(recipientNode.asString()), primaryFieldName, required);
        }
        if (!recipientNode.isArray()) {
            throw new IllegalArgumentException(primaryFieldName + " must be an array");
        }
        java.util.ArrayList<String> recipients = new java.util.ArrayList<>();
        for (JsonNode singleRecipientNode : recipientNode) {
            if (!singleRecipientNode.isString()) {
                throw new IllegalArgumentException(primaryFieldName + " must contain strings");
            }
            recipients.add(singleRecipientNode.asString());
        }
        return validateRecipients(recipients, primaryFieldName, required);
    }

    private static List<String> validateRecipients(
            List<String> recipients, String fieldName, boolean required) {
        return required
                ? EmailRecipientValidator.required(recipients, fieldName)
                : EmailRecipientValidator.optional(recipients, fieldName);
    }
}
