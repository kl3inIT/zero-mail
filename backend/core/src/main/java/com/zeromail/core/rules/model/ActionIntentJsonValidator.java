package com.zeromail.core.rules.model;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ActionIntentJsonValidator {

  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

  public void validateActionIntentsJson(String actionIntentsJson) {
    JsonNode actionIntentRoot = readJson(actionIntentsJson);
    if (!actionIntentRoot.isArray() || actionIntentRoot.isEmpty()) {
      throw new IllegalArgumentException("actionIntentsJson must be a non-empty JSON array");
    }
    for (JsonNode actionIntentNode : actionIntentRoot) {
      RuleActionType.fromId(actionType(actionIntentNode));
    }
  }

  private static JsonNode readJson(String actionIntentsJson) {
    if (actionIntentsJson == null || actionIntentsJson.isBlank()) {
      throw new IllegalArgumentException("actionIntentsJson must not be blank");
    }
    try {
      return OBJECT_MAPPER.readTree(actionIntentsJson);
    } catch (JacksonException jacksonException) {
      throw new IllegalArgumentException("actionIntentsJson must be valid JSON", jacksonException);
    }
  }

  private static String actionType(JsonNode actionIntentNode) {
    JsonNode typeNode = actionIntentNode.path("type");
    JsonNode selectedNode =
        typeNode.isMissingNode() || typeNode.isNull() ? actionIntentNode.path("action") : typeNode;
    if (selectedNode.isMissingNode() || selectedNode.isNull() || !selectedNode.isString()) {
      throw new IllegalArgumentException("action intent type is required");
    }
    return selectedNode.asString();
  }
}
