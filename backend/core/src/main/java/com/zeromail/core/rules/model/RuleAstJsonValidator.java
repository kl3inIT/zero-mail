package com.zeromail.core.rules.model;

import java.util.Objects;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class RuleAstJsonValidator {

  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

  public void validateMatcherJson(String matcherJson) {
    JsonNode matcherRoot = readJson(matcherJson);
    validateSchemaVersion(matcherRoot);
    validateMatcherNode(matcherRoot);
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

  private static void validateMatcherNode(JsonNode matcherNode) {
    String matcherTypeId = textValue(matcherNode, "type", "matcherType");
    MatcherType matcherType = MatcherType.fromId(matcherTypeId);
    if (matcherType == MatcherType.ALL || matcherType == MatcherType.ANY) {
      JsonNode childrenNode = required(matcherNode, "children");
      if (!childrenNode.isArray() || childrenNode.isEmpty()) {
        throw new IllegalArgumentException(matcherType.id() + " children must be a non-empty array");
      }
      for (JsonNode childNode : childrenNode) {
        validateMatcherNode(childNode);
      }
    }
    if (matcherType == MatcherType.NOT) {
      validateMatcherNode(required(matcherNode, "child"));
    }
    if (matcherType == MatcherType.SEMANTIC_INTENT) {
      JsonNode deferredNode = required(matcherNode, "deferred");
      if (!deferredNode.isBoolean() || !deferredNode.booleanValue()) {
        throw new IllegalArgumentException("SEMANTIC_INTENT matcher must be deferred");
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

  private static String textValue(JsonNode jsonNode, String primaryFieldName, String fallbackFieldName) {
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
}
