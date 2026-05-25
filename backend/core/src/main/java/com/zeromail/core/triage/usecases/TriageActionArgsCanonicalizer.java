package com.zeromail.core.triage.usecases;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.shared.crypto.Hashing;
import com.zeromail.core.shared.validation.EmailRecipientValidator;
import com.zeromail.core.triage.domain.TriageActionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class TriageActionArgsCanonicalizer {

    private final TriageActionResultJsonValidator actionResultJsonValidator;

    public TriageActionArgsCanonicalizer() {
        this(new TriageActionResultJsonValidator());
    }

    public TriageActionArgsCanonicalizer(
            TriageActionResultJsonValidator actionResultJsonValidator) {
        this.actionResultJsonValidator = actionResultJsonValidator;
    }

    /**
     * Returns the raw 32-byte SHA-256 hash for the pre-write intent. Gmail-returned draft ids are
     * removed before hashing so a post-write SaveDraft payload still maps to the same PENDING row.
     */
    public byte[] canonicalHash(TriageActionResult preWriteIntent) {
        return Hashing.sha256(canonicalJson(preWriteIntent));
    }

    public byte[] canonicalHash(String actionArgsJson) {
        return Hashing.sha256(canonicalJson(actionArgsJson));
    }

    public String canonicalJson(TriageActionResult preWriteIntent) {
        return canonicalJson(actionResultJsonValidator.toJson(preWriteIntent));
    }

    public String canonicalJson(String actionArgsJson) {
        JsonNode actionResultNode = TriageActionResultJsonValidator.readJson(actionArgsJson);
        if (!actionResultNode.isObject()) {
            throw new IllegalArgumentException("actionArgsJson must be a JSON object");
        }
        RuleActionType actionType = RuleActionType.fromId(actionType(actionResultNode));
        TreeMap<String, Object> canonicalFields = new TreeMap<>();
        canonicalFields.put("type", actionType.id());
        switch (actionType) {
            case LABEL -> {
                canonicalFields.put("labelId", requiredText(actionResultNode, "labelId"));
                canonicalFields.put("labelName", requiredText(actionResultNode, "labelName"));
            }
            case ARCHIVE -> {
                // Type alone is the full canonical intent for archive.
            }
            case SAVE_DRAFT -> {
                canonicalFields.put("instruction", requiredText(actionResultNode, "instruction"));
                // Draft idempotency is intentionally thread-scoped: one draft per matched thread
                // and
                // instruction, even when multiple messages in that thread trigger the same rule.
                canonicalFields.put("threadId", requiredText(actionResultNode, "threadId"));
            }
            case MARK_READ, STAR, ADD_TO_DIGEST, MARK_SPAM -> {
                // Type alone is the full canonical intent for no-argument Gmail write actions.
            }
            case SEND_REPLY -> {
                canonicalFields.put("body", requiredText(actionResultNode, "body", "draftBody"));
                canonicalFields.put(
                        "gmailMessageId", requiredText(actionResultNode, "gmailMessageId"));
                canonicalFields.put(
                        "gmailThreadId", requiredText(actionResultNode, "gmailThreadId"));
            }
            case FORWARD_EMAIL -> {
                canonicalFields.put("body", requiredText(actionResultNode, "body", "draftBody"));
                canonicalFields.put("recipients", recipients(actionResultNode, "recipients"));
            }
            case SEND_EMAIL -> {
                canonicalFields.put("bcc", optionalRecipients(actionResultNode, "bcc"));
                canonicalFields.put("body", requiredText(actionResultNode, "body", "draftBody"));
                canonicalFields.put("cc", optionalRecipients(actionResultNode, "cc"));
                canonicalFields.put("subject", requiredText(actionResultNode, "subject"));
                canonicalFields.put("to", recipients(actionResultNode, "to"));
            }
            default -> throw new IllegalStateException("Unhandled rule action type: " + actionType);
        }
        return TriageActionResultJsonValidator.writeJson(canonicalFields);
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

    private static String requiredText(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull() || !fieldNode.isString()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String value = fieldNode.asString().trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String requiredText(
            JsonNode jsonNode, String primaryFieldName, String fallbackFieldName) {
        JsonNode fieldNode = jsonNode.path(primaryFieldName);
        if ((fieldNode.isMissingNode() || fieldNode.isNull())
                && !primaryFieldName.equals(fallbackFieldName)) {
            fieldNode = jsonNode.path(fallbackFieldName);
        }
        if (fieldNode.isMissingNode() || fieldNode.isNull() || !fieldNode.isString()) {
            throw new IllegalArgumentException(primaryFieldName + " is required");
        }
        String value = fieldNode.asString().trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(primaryFieldName + " must not be blank");
        }
        return value;
    }

    private static List<String> recipients(JsonNode jsonNode, String fieldName) {
        return EmailRecipientValidator.required(recipientValues(jsonNode, fieldName), fieldName);
    }

    private static List<String> optionalRecipients(JsonNode jsonNode, String fieldName) {
        return EmailRecipientValidator.optional(recipientValues(jsonNode, fieldName), fieldName);
    }

    private static List<String> recipientValues(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return List.of();
        }
        if (!fieldNode.isArray()) {
            throw new IllegalArgumentException(fieldName + " must be an array");
        }
        ArrayList<String> recipients = new ArrayList<>();
        for (JsonNode recipientNode : fieldNode) {
            if (!recipientNode.isString()) {
                throw new IllegalArgumentException(fieldName + " must contain strings");
            }
            String recipient = recipientNode.asString().trim();
            recipients.add(recipient);
        }
        return List.copyOf(recipients);
    }
}
