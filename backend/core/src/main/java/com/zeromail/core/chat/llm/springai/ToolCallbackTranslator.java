package com.zeromail.core.chat.llm.springai;

import com.zeromail.core.chat.usecases.ChatToolCatalog;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ToolCallbackTranslator {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    public List<ToolCallback> translate(ChatToolCatalog chatToolCatalog) {
        chatToolCatalog.validate();
        return chatToolCatalog.toolDefinitions().stream().map(this::translate).toList();
    }

    private ToolCallback translate(ChatToolCatalog.ToolDefinition toolDefinition) {
        return FunctionToolCallback.builder(
                        toolDefinition.name().id(), (Object ignoredInput) -> Map.of())
                .description(toolDefinition.description())
                .inputSchema(inputSchemaFor(toolDefinition.argsRecordClass()))
                .inputType(toolDefinition.argsRecordClass())
                .build();
    }

    static String inputSchemaFor(Class<?> argsRecordClass) {
        return ensureObjectSchemaProperties(JsonSchemaGenerator.generateForType(argsRecordClass));
    }

    private static String ensureObjectSchemaProperties(String generatedSchema) {
        try {
            JsonNode schemaNode = OBJECT_MAPPER.readTree(generatedSchema);
            if (schemaNode instanceof ObjectNode objectSchemaNode
                    && isObjectSchemaWithoutProperties(objectSchemaNode)) {
                objectSchemaNode.set("properties", OBJECT_MAPPER.createObjectNode());
                return OBJECT_MAPPER.writeValueAsString(objectSchemaNode);
            }
            return generatedSchema;
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "Tool input schema could not be parsed", jacksonException);
        }
    }

    private static boolean isObjectSchemaWithoutProperties(ObjectNode objectSchemaNode) {
        JsonNode typeNode = objectSchemaNode.get("type");
        return typeNode != null
                && "object".equals(typeNode.asString())
                && !objectSchemaNode.has("properties");
    }
}
