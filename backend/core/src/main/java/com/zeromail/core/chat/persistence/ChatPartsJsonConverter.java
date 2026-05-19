package com.zeromail.core.chat.persistence;

import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ChatPartsJsonConverter {

    private final ObjectMapper objectMapper;

    public ChatPartsJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(ChatMessageParts chatMessageParts) {
        try {
            return objectMapper.writeValueAsString(ChatPartsSchemaV1.serialize(chatMessageParts));
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException(
                    "chat message parts could not be serialized", jacksonException);
        }
    }

    public ChatMessageParts fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("chat message parts JSON must not be blank");
        }
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            JsonNode schemaVersionNode = rootNode.path("schemaVersion");
            if (schemaVersionNode.isMissingNode() || schemaVersionNode.isNull()) {
                throw new IllegalStateException("Unknown chat parts schemaVersion: missing");
            }
            int schemaVersion = schemaVersionNode.asInt();
            if (schemaVersion != ChatMessageParts.CURRENT_SCHEMA_VERSION) {
                throw new IllegalStateException(
                        "Unknown chat parts schemaVersion: "
                                + schemaVersion
                                + " - add a dispatcher branch");
            }
            return ChatPartsSchemaV1.deserialize(rootNode);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException(
                    "chat message parts JSON must be valid", jacksonException);
        }
    }

    public String canonicalJson(String json) {
        return toJson(fromJson(json));
    }
}
