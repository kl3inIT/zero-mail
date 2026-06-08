package com.zeromail.core.chat.llm.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.usecases.ChatToolCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ToolCallbackTranslatorTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void translates_empty_record_tool_args_to_object_schema_with_empty_properties()
            throws JacksonException {
        ToolCallbackTranslator toolCallbackTranslator = new ToolCallbackTranslator();

        JsonNode listLabelsSchema =
                schemaFor(
                        toolCallbackTranslator.translate(new ChatToolCatalog()),
                        ChatToolName.LIST_LABELS);
        JsonNode listRulesSchema =
                schemaFor(
                        toolCallbackTranslator.translate(new ChatToolCatalog()),
                        ChatToolName.LIST_RULES);

        assertEmptyPropertiesObject(listLabelsSchema);
        assertEmptyPropertiesObject(listRulesSchema);
    }

    @Test
    void preserves_generated_properties_for_non_empty_tool_args() throws JacksonException {
        JsonNode getMessageSchema =
                objectMapper.readTree(
                        ToolCallbackTranslator.inputSchemaFor(
                                ChatToolCatalog.GetMessageArgs.class));

        assertThat(getMessageSchema.get("type").asString()).isEqualTo("object");
        assertThat(getMessageSchema.get("properties").has("messageId")).isTrue();
    }

    @Test
    void confirmed_reply_and_forward_schemas_include_send_executor_fields()
            throws JacksonException {
        ToolCallbackTranslator toolCallbackTranslator = new ToolCallbackTranslator();
        List<ToolCallback> callbacks = toolCallbackTranslator.translate(new ChatToolCatalog());

        JsonNode replyEmailSchema = schemaFor(callbacks, ChatToolName.REPLY_EMAIL);
        JsonNode forwardEmailSchema = schemaFor(callbacks, ChatToolName.FORWARD_EMAIL);

        assertThat(replyEmailSchema.get("properties").has("subject")).isTrue();
        assertThat(replyEmailSchema.get("properties").has("gmailThreadId")).isTrue();
        assertThat(replyEmailSchema.get("properties").has("cc")).isTrue();
        assertThat(forwardEmailSchema.get("properties").has("subject")).isTrue();
        assertThat(forwardEmailSchema.get("properties").has("gmailThreadId")).isTrue();
        assertThat(forwardEmailSchema.get("properties").has("cc")).isTrue();
    }

    @Test
    void send_reply_forward_schemas_describe_recipient_as_email_address() throws JacksonException {
        ToolCallbackTranslator toolCallbackTranslator = new ToolCallbackTranslator();
        List<ToolCallback> callbacks = toolCallbackTranslator.translate(new ChatToolCatalog());

        for (ChatToolName sendTool :
                List.of(
                        ChatToolName.SEND_EMAIL,
                        ChatToolName.REPLY_EMAIL,
                        ChatToolName.FORWARD_EMAIL)) {
            JsonNode toProperty = schemaFor(callbacks, sendTool).get("properties").get("to");
            assertThat(toProperty).as("%s.to property", sendTool.id()).isNotNull();
            assertThat(toProperty.get("description").asString())
                    .as("%s.to description steers the model to a real email address", sendTool.id())
                    .containsIgnoringCase("email address");
        }
    }

    private JsonNode schemaFor(List<ToolCallback> toolCallbacks, ChatToolName chatToolName)
            throws JacksonException {
        String schema =
                toolCallbacks.stream()
                        .filter(
                                toolCallback ->
                                        chatToolName
                                                .id()
                                                .equals(toolCallback.getToolDefinition().name()))
                        .findFirst()
                        .orElseThrow()
                        .getToolDefinition()
                        .inputSchema();
        return objectMapper.readTree(schema);
    }

    private static void assertEmptyPropertiesObject(JsonNode schemaNode) {
        assertThat(schemaNode.get("type").asString()).isEqualTo("object");
        assertThat(schemaNode.get("properties").isObject()).isTrue();
        assertThat(schemaNode.get("properties").size()).isZero();
    }
}
