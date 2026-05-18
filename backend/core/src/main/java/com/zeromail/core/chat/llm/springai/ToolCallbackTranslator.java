package com.zeromail.core.chat.llm.springai;

import com.zeromail.core.chat.usecases.ChatToolCatalog;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
public class ToolCallbackTranslator {

    public List<ToolCallback> translate(ChatToolCatalog chatToolCatalog) {
        chatToolCatalog.validate();
        return chatToolCatalog.toolDefinitions().stream().map(this::translate).toList();
    }

    private ToolCallback translate(ChatToolCatalog.ToolDefinition toolDefinition) {
        return FunctionToolCallback.builder(
                        toolDefinition.name().id(), (Object ignoredInput) -> Map.of())
                .description(toolDefinition.description())
                .inputSchema(JsonSchemaGenerator.generateForType(toolDefinition.argsRecordClass()))
                .inputType(toolDefinition.argsRecordClass())
                .build();
    }
}
