package com.zeromail.core.chat.sanitize;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ToolOutputSanitizerTest {

    private static final String VIETNAMESE_DRAFT_BODY =
            "Xin chào anh, em gửi bản nháp này để anh xem trước khi bấm gửi.";

    private final ToolOutputSanitizer sanitizer = new ToolOutputSanitizer();

    @Test
    void strips_body_fields_from_email_read_and_unknown_tool_outputs(
            CapturedOutput capturedOutput) {
        ChatMessageParts parts =
                ChatMessageParts.v1(
                        List.of(
                                outputPart(
                                        "searchInbox",
                                        Map.of("messages", List.of(Map.of("htmlBody", "secret")))),
                                outputPart("getMessage", Map.of("bodyText", "secret")),
                                outputPart(
                                        "getThread",
                                        Map.of("thread", Map.of("messageBody", "secret"))),
                                outputPart(
                                        "listLabels",
                                        Map.of("labels", List.of(Map.of("emailBody", "secret")))),
                                outputPart("unknownFutureTool", Map.of("body", "secret"))));

        ChatMessageParts sanitized =
                sanitizer.sanitize(parts, "00000000-0000-4000-8000-000000070002");

        assertThat(sanitized.schemaVersion()).isEqualTo(1);
        assertThat(sanitized.parts())
                .allSatisfy(part -> assertThat(part.toString()).doesNotContain("secret"));
        assertThat(capturedOutput.getOut() + capturedOutput.getErr())
                .contains("event=chat_tool_output_sanitized", "toolSource=read")
                .doesNotContain("secret");
    }

    @Test
    void preserves_user_authored_draft_body_fields_for_send_and_draft_tools(
            CapturedOutput capturedOutput) {
        ChatMessageParts parts =
                ChatMessageParts.v1(
                        List.of(
                                callPart("sendEmail", Map.of("body", VIETNAMESE_DRAFT_BODY)),
                                callPart("replyEmail", Map.of("bodyText", VIETNAMESE_DRAFT_BODY)),
                                callPart("forwardEmail", Map.of("htmlBody", VIETNAMESE_DRAFT_BODY)),
                                callPart(
                                        "saveDraft",
                                        Map.of("messageBody", VIETNAMESE_DRAFT_BODY))));

        ChatMessageParts sanitized =
                sanitizer.sanitize(parts, "00000000-0000-4000-8000-000000070002");

        assertThat(sanitized.parts().toString()).contains(VIETNAMESE_DRAFT_BODY);
        assertThat(capturedOutput.getOut() + capturedOutput.getErr())
                .contains("event=chat_tool_output_sanitized", "toolSource=draft")
                .doesNotContain(VIETNAMESE_DRAFT_BODY);
    }

    @Test
    void length_cap_applies_after_source_aware_body_strip() {
        String longBody = "a".repeat(5000);
        ChatMessageParts parts =
                ChatMessageParts.v1(
                        List.of(
                                callPart("sendEmail", Map.of("body", longBody)),
                                outputPart(
                                        "getMessage",
                                        Map.of("body", longBody, "metadata", "safe"))));

        ChatMessageParts sanitized =
                sanitizer.sanitize(parts, "00000000-0000-4000-8000-000000070002");

        ToolCallPart sendEmailPart = (ToolCallPart) sanitized.parts().getFirst();
        ToolOutputPart getMessagePart = (ToolOutputPart) sanitized.parts().get(1);
        assertThat(sendEmailPart.truncated()).isTrue();
        assertThat(sendEmailPart.inputJson().get("body")).isEqualTo("a".repeat(4000));
        assertThat(getMessagePart.truncated()).isFalse();
        assertThat(getMessagePart.outputJson())
                .containsEntry("metadata", "safe")
                .doesNotContainKey("body");
    }

    @Test
    void java_body_signatures_match_postgres_trigger_field_names() throws Exception {
        String changelog;
        try (InputStream changelogStream =
                Objects.requireNonNull(
                        getClass()
                                .getClassLoader()
                                .getResourceAsStream(
                                        "db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml"))) {
            changelog = new String(changelogStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(BodyContentSignatures.BODY_FIELD_NAMES)
                .allSatisfy(fieldName -> assertThat(changelog).contains(fieldName));
    }

    private static ToolOutputPart outputPart(String toolName, Map<String, Object> outputJson) {
        return new ToolOutputPart(
                "part-" + toolName,
                "tool-call-" + toolName,
                toolName,
                "output-available",
                outputJson,
                false);
    }

    private static ToolCallPart callPart(String toolName, Map<String, Object> inputJson) {
        return new ToolCallPart(
                "part-" + toolName,
                "tool-call-" + toolName,
                toolName,
                "input-available",
                inputJson,
                false);
    }
}
