package com.zeromail.core.triage.usecases;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.domain.TriageActionResult;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TriageActionResultJsonValidator {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final int MAX_ACTION_TEXT_LENGTH = 500;
    private static final int MAX_ACTION_BODY_LENGTH = 4000;
    private static final Set<String> COMMON_FIELDS = Set.of("type", "action");

    public void validateActionArgsJson(String actionArgsJson) {
        parseActionArgsJson(actionArgsJson);
    }

    public TriageActionResult parseActionArgsJson(String actionArgsJson) {
        JsonNode actionResultNode = readJson(actionArgsJson);
        if (!actionResultNode.isObject()) {
            throw new IllegalArgumentException("actionArgsJson must be a JSON object");
        }
        RuleActionType actionType = RuleActionType.fromId(actionType(actionResultNode));
        return switch (actionType) {
            case LABEL -> parseLabel(actionResultNode);
            case ARCHIVE -> parseArchive(actionResultNode);
            case SAVE_DRAFT -> parseSaveDraft(actionResultNode);
            case MARK_READ -> parseMarkRead(actionResultNode);
            case STAR -> parseStar(actionResultNode);
            case ADD_TO_DIGEST -> parseAddToDigest(actionResultNode);
            case MARK_SPAM -> parseMarkSpam(actionResultNode);
            case SEND_REPLY -> parseSendReply(actionResultNode);
            case FORWARD_EMAIL -> parseForwardEmail(actionResultNode);
            case SEND_EMAIL -> parseSendEmail(actionResultNode);
        };
    }

    public void validate(TriageActionResult actionResult) {
        if (actionResult == null) {
            throw new IllegalArgumentException("actionResult must not be null");
        }
        validateActionArgsJson(toJson(actionResult));
    }

    public String toJson(TriageActionResult actionResult) {
        validateNotNull(actionResult);
        Map<String, Object> jsonFields = new LinkedHashMap<>();
        switch (actionResult) {
            case TriageActionResult.Label label -> {
                jsonFields.put("type", RuleActionType.LABEL.id());
                jsonFields.put("labelId", label.labelId());
                jsonFields.put("labelName", label.labelName());
            }
            case TriageActionResult.Archive ignored ->
                    jsonFields.put("type", RuleActionType.ARCHIVE.id());
            case TriageActionResult.SaveDraft saveDraft -> {
                jsonFields.put("type", RuleActionType.SAVE_DRAFT.id());
                jsonFields.put("instruction", saveDraft.instruction());
                jsonFields.put("draftId", saveDraft.draftId());
                jsonFields.put("threadId", saveDraft.threadId());
            }
            case TriageActionResult.MarkRead ignored ->
                    jsonFields.put("type", RuleActionType.MARK_READ.id());
            case TriageActionResult.Star ignored ->
                    jsonFields.put("type", RuleActionType.STAR.id());
            case TriageActionResult.AddToDigest ignored ->
                    jsonFields.put("type", RuleActionType.ADD_TO_DIGEST.id());
            case TriageActionResult.MarkSpam ignored ->
                    jsonFields.put("type", RuleActionType.MARK_SPAM.id());
            case TriageActionResult.SendReply sendReply -> {
                jsonFields.put("type", RuleActionType.SEND_REPLY.id());
                jsonFields.put("body", sendReply.draftBody());
                jsonFields.put("gmailMessageId", sendReply.gmailMessageId());
                jsonFields.put("gmailThreadId", sendReply.gmailThreadId());
            }
            case TriageActionResult.ForwardEmail forwardEmail -> {
                jsonFields.put("type", RuleActionType.FORWARD_EMAIL.id());
                jsonFields.put("recipients", forwardEmail.recipients());
                jsonFields.put("body", forwardEmail.draftBody());
            }
            case TriageActionResult.SendEmail sendEmail -> {
                jsonFields.put("type", RuleActionType.SEND_EMAIL.id());
                jsonFields.put("to", sendEmail.to());
                jsonFields.put("cc", sendEmail.cc());
                jsonFields.put("bcc", sendEmail.bcc());
                jsonFields.put("subject", sendEmail.subject());
                jsonFields.put("body", sendEmail.draftBody());
            }
        }
        return writeJson(jsonFields);
    }

    public String toCanonicalJson(TriageActionResult actionResult) {
        validateNotNull(actionResult);
        return new TriageActionArgsCanonicalizer().canonicalJson(actionResult);
    }

    private static TriageActionResult.Label parseLabel(JsonNode actionResultNode) {
        rejectUnknownFields(actionResultNode, Set.of("labelId", "labelName"));
        return new TriageActionResult.Label(
                requiredText(actionResultNode, "labelId", MAX_ACTION_TEXT_LENGTH),
                requiredText(actionResultNode, "labelName", MAX_ACTION_TEXT_LENGTH));
    }

    private static TriageActionResult.Archive parseArchive(JsonNode actionResultNode) {
        rejectUnknownFields(actionResultNode, Set.of());
        return new TriageActionResult.Archive();
    }

    private static TriageActionResult.SaveDraft parseSaveDraft(JsonNode actionResultNode) {
        rejectUnknownFields(actionResultNode, Set.of("instruction", "draftId", "threadId"));
        return new TriageActionResult.SaveDraft(
                requiredText(actionResultNode, "instruction", MAX_ACTION_TEXT_LENGTH),
                optionalText(actionResultNode, "draftId", MAX_ACTION_TEXT_LENGTH),
                requiredText(actionResultNode, "threadId", MAX_ACTION_TEXT_LENGTH));
    }

    private static TriageActionResult.MarkRead parseMarkRead(JsonNode actionResultNode) {
        rejectUnknownFields(actionResultNode, Set.of());
        return new TriageActionResult.MarkRead();
    }

    private static TriageActionResult.Star parseStar(JsonNode actionResultNode) {
        rejectUnknownFields(actionResultNode, Set.of());
        return new TriageActionResult.Star();
    }

    private static TriageActionResult.AddToDigest parseAddToDigest(JsonNode actionResultNode) {
        rejectUnknownFields(actionResultNode, Set.of());
        return new TriageActionResult.AddToDigest();
    }

    private static TriageActionResult.MarkSpam parseMarkSpam(JsonNode actionResultNode) {
        rejectUnknownFields(actionResultNode, Set.of());
        return new TriageActionResult.MarkSpam();
    }

    private static TriageActionResult.SendReply parseSendReply(JsonNode actionResultNode) {
        rejectUnknownFields(
                actionResultNode, Set.of("body", "draftBody", "gmailMessageId", "gmailThreadId"));
        return new TriageActionResult.SendReply(
                requiredText(actionResultNode, "body", "draftBody", MAX_ACTION_BODY_LENGTH),
                requiredText(actionResultNode, "gmailMessageId", MAX_ACTION_TEXT_LENGTH),
                requiredText(actionResultNode, "gmailThreadId", MAX_ACTION_TEXT_LENGTH));
    }

    private static TriageActionResult.ForwardEmail parseForwardEmail(JsonNode actionResultNode) {
        rejectUnknownFields(actionResultNode, Set.of("recipients", "body", "draftBody"));
        return new TriageActionResult.ForwardEmail(
                recipients(actionResultNode, "recipients"),
                requiredText(actionResultNode, "body", "draftBody", MAX_ACTION_BODY_LENGTH));
    }

    private static TriageActionResult.SendEmail parseSendEmail(JsonNode actionResultNode) {
        rejectUnknownFields(
                actionResultNode, Set.of("to", "cc", "bcc", "subject", "body", "draftBody"));
        return new TriageActionResult.SendEmail(
                recipients(actionResultNode, "to"),
                optionalRecipients(actionResultNode, "cc"),
                optionalRecipients(actionResultNode, "bcc"),
                requiredText(actionResultNode, "subject", MAX_ACTION_TEXT_LENGTH),
                requiredText(actionResultNode, "body", "draftBody", MAX_ACTION_BODY_LENGTH));
    }

    static JsonNode readJson(String actionArgsJson) {
        if (actionArgsJson == null || actionArgsJson.isBlank()) {
            throw new IllegalArgumentException("actionArgsJson must not be blank");
        }
        try {
            return OBJECT_MAPPER.readTree(actionArgsJson);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException(
                    "actionArgsJson must be valid JSON", jacksonException);
        }
    }

    static String writeJson(Map<String, Object> jsonFields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(jsonFields);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException(
                    "actionArgsJson could not be serialized", jacksonException);
        }
    }

    private static String actionType(JsonNode actionResultNode) {
        JsonNode typeNode = actionResultNode.path("type");
        JsonNode selectedNode =
                typeNode.isMissingNode() || typeNode.isNull()
                        ? actionResultNode.path("action")
                        : typeNode;
        if (selectedNode.isMissingNode() || selectedNode.isNull() || !selectedNode.isString()) {
            throw new IllegalArgumentException("action result type is required");
        }
        return selectedNode.asString();
    }

    private static void rejectUnknownFields(
            JsonNode actionResultNode, Set<String> actionSpecificFields) {
        Set<String> allowedFields = new LinkedHashSet<>(COMMON_FIELDS);
        allowedFields.addAll(actionSpecificFields);
        for (Map.Entry<String, JsonNode> property : actionResultNode.properties()) {
            String fieldName = property.getKey();
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("unknown action result field");
            }
        }
    }

    private static String requiredText(JsonNode jsonNode, String fieldName, int maxLength) {
        String value = optionalText(jsonNode, fieldName, maxLength);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String requiredText(
            JsonNode jsonNode, String primaryFieldName, String fallbackFieldName, int maxLength) {
        String value = optionalText(jsonNode, primaryFieldName, maxLength);
        if (value == null && !primaryFieldName.equals(fallbackFieldName)) {
            value = optionalText(jsonNode, fallbackFieldName, maxLength);
        }
        if (value == null) {
            throw new IllegalArgumentException(primaryFieldName + " is required");
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

    private static List<String> recipients(JsonNode jsonNode, String fieldName) {
        List<String> recipients = optionalRecipients(jsonNode, fieldName);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return recipients;
    }

    private static List<String> optionalRecipients(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return List.of();
        }
        if (!fieldNode.isArray()) {
            throw new IllegalArgumentException(fieldName + " must be an array");
        }
        java.util.ArrayList<String> recipients = new java.util.ArrayList<>();
        for (JsonNode recipientNode : fieldNode) {
            if (!recipientNode.isString()) {
                throw new IllegalArgumentException(fieldName + " must contain strings");
            }
            recipients.add(recipientNode.asString());
        }
        return List.copyOf(recipients);
    }

    private static void validateNotNull(TriageActionResult actionResult) {
        if (actionResult == null) {
            throw new IllegalArgumentException("actionResult must not be null");
        }
    }
}
