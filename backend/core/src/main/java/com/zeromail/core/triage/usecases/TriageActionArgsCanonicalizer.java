package com.zeromail.core.triage.usecases;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.shared.crypto.Hashing;
import com.zeromail.core.triage.domain.TriageActionResult;
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
            case MARK_READ, STAR, ADD_TO_DIGEST, MARK_SPAM, SEND_REPLY, FORWARD_EMAIL, SEND_EMAIL ->
                    throw new IllegalArgumentException(
                            actionType.id() + " action result is not supported yet");
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
}
