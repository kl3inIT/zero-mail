package com.zeromail.core.chat.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ChatPartsSchemaV1Test {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final ChatPartsJsonConverter converter = new ChatPartsJsonConverter(objectMapper);

    @Test
    void v1_fixtures_round_trip_through_schema_dispatcher() throws Exception {
        for (String fixtureName :
                List.of(
                        "text-only.json",
                        "single-tool-call.json",
                        "multi-tool-call-confirmed-send.json",
                        "send-email-with-draft-body.json")) {
            String fixtureJson = fixture(fixtureName);

            ChatMessageParts parts = converter.fromJson(fixtureJson);
            String roundTripJson = converter.toJson(parts);

            assertThat(parts.schemaVersion()).isEqualTo(1);
            assertThat(json(roundTripJson)).isEqualTo(json(converter.canonicalJson(fixtureJson)));
        }
    }

    @Test
    void assistant_text_part_round_trips_without_default_branch() {
        Instant completedAt = Instant.parse("2026-05-18T02:26:28Z");
        ChatMessageParts parts =
                ChatMessageParts.v1(
                        List.of(
                                new AssistantTextPart(
                                        "assistant-part-1", "Đã chuẩn bị bản nháp.", completedAt)));

        ChatMessageParts roundTripped = converter.fromJson(converter.toJson(parts));

        assertThat(roundTripped.parts()).singleElement().isInstanceOf(AssistantTextPart.class);
        AssistantTextPart assistantTextPart = (AssistantTextPart) roundTripped.parts().getFirst();
        assertThat(assistantTextPart.partId()).isEqualTo("assistant-part-1");
        assertThat(assistantTextPart.text()).isEqualTo("Đã chuẩn bị bản nháp.");
        assertThat(assistantTextPart.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void tool_parts_keep_input_output_and_confirmation_payloads() throws Exception {
        ChatMessageParts parts = converter.fromJson(fixture("multi-tool-call-confirmed-send.json"));

        assertThat(parts.parts())
                .anySatisfy(part -> assertThat(part).isInstanceOf(ToolOutputPart.class));
        ToolOutputPart sendEmailPart =
                parts.parts().stream()
                        .filter(ToolOutputPart.class::isInstance)
                        .map(ToolOutputPart.class::cast)
                        .filter(part -> part.toolName().equals("sendEmail"))
                        .findFirst()
                        .orElseThrow();
        assertThat(sendEmailPart.inputJson()).containsKeys("to", "subject", "body");
        assertThat(sendEmailPart.confirmationJson()).containsEntry("state", "confirmed");
        assertThat(sendEmailPart.outputJson()).containsEntry("status", "sent");

        ChatMessageParts draftOnlyParts =
                converter.fromJson(fixture("send-email-with-draft-body.json"));
        assertThat(draftOnlyParts.parts()).singleElement().isInstanceOf(ToolCallPart.class);
    }

    private String fixture(String fixtureName) throws Exception {
        try (var fixtureStream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("chat-message-fixtures/v1/" + fixtureName)) {
            assertThat(fixtureStream).isNotNull();
            return new String(fixtureStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private JsonNode json(String json) {
        return objectMapper.readTree(json);
    }
}
