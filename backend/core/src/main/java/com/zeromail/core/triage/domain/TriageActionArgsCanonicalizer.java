package com.zeromail.core.triage.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import com.zeromail.core.rules.domain.RuleActionType;

import tools.jackson.databind.JsonNode;

public class TriageActionArgsCanonicalizer {

  private final TriageActionResultJsonValidator actionResultJsonValidator;

  public TriageActionArgsCanonicalizer() {
    this(new TriageActionResultJsonValidator());
  }

  public TriageActionArgsCanonicalizer(TriageActionResultJsonValidator actionResultJsonValidator) {
    this.actionResultJsonValidator = actionResultJsonValidator;
  }

  /**
   * Returns the raw 32-byte SHA-256 hash for the pre-write intent. Gmail-returned draft ids are
   * removed before hashing so a post-write SaveDraft payload still maps to the same PENDING row.
   */
  public byte[] canonicalHash(TriageActionResult preWriteIntent) {
    return sha256(canonicalJson(preWriteIntent));
  }

  public byte[] canonicalHash(String actionArgsJson) {
    return sha256(canonicalJson(actionArgsJson));
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
        canonicalFields.put("threadId", requiredText(actionResultNode, "threadId"));
      }
    }
    return TriageActionResultJsonValidator.writeJson(canonicalFields);
  }

  private static String actionType(JsonNode actionResultNode) {
    JsonNode typeNode = actionResultNode.path("type");
    JsonNode selectedNode =
        typeNode.isMissingNode() || typeNode.isNull() ? actionResultNode.path("action") : typeNode;
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

  private static byte[] sha256(String canonicalJson) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      return messageDigest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
      throw new IllegalStateException("SHA-256 digest is unavailable", noSuchAlgorithmException);
    }
  }
}
