package com.zeromail.core.chat.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class VercelProtocolEmitterTest {

    private static ObjectMapper testMapper() {
        return JsonMapper.builder().build();
    }

    @Test
    void rejects_text_delta_before_text_start() {
        VercelProtocolEmitter emitter = new VercelProtocolEmitter(ignoredFrame -> {}, testMapper());

        assertThatThrownBy(() -> emitter.emitTextDelta("part-1", "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("text delta before text start");
    }

    @Test
    void emits_ordered_json_frames_for_text_and_tools() {
        List<String> frames = new ArrayList<>();
        VercelProtocolEmitter emitter =
                new VercelProtocolEmitter(frames::add, testMapper(), "assistant-message-1");

        emitter.emitTextStart("part-1");
        emitter.emitTextDelta("part-1", "Xin");
        emitter.emitTextEnd("part-1");
        emitter.emitToolInputStart("call-1", "searchInbox");
        emitter.emitToolInputAvailable("call-1", "searchInbox", "{\"query\":\"Acme\"}");
        emitter.emitToolOutputAvailable("call-1", "{\"resultCount\":1}");
        emitter.emitFinish("stop");

        assertThat(frames)
                .containsExactly(
                        "{\"type\":\"start\",\"messageId\":\"assistant-message-1\"}",
                        "{\"type\":\"text-start\",\"id\":\"part-1\"}",
                        "{\"type\":\"text-delta\",\"id\":\"part-1\",\"delta\":\"Xin\"}",
                        "{\"type\":\"text-end\",\"id\":\"part-1\"}",
                        "{\"type\":\"tool-input-start\",\"toolCallId\":\"call-1\",\"toolName\":\"searchInbox\"}",
                        "{\"type\":\"tool-input-available\",\"toolCallId\":\"call-1\",\"toolName\":\"searchInbox\",\"input\":\"{\\\"query\\\":\\\"Acme\\\"}\"}",
                        "{\"type\":\"tool-output-available\",\"toolCallId\":\"call-1\",\"output\":\"{\\\"resultCount\\\":1}\"}",
                        "{\"type\":\"finish\",\"finishReason\":\"stop\"}");
    }

    @Test
    void emits_ai_sdk_v6_compatible_data_and_finish_frames() {
        List<String> frames = new ArrayList<>();
        VercelProtocolEmitter emitter =
                new VercelProtocolEmitter(frames::add, testMapper(), "assistant-message-2");
        UUID chatMessageId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        emitter.emitDataPersistence(chatMessageId, "assistant-message-saved");
        emitter.emitFinish("awaiting-confirmation");
        emitter.emitError("chat_stream_error", "The assistant stream failed.");

        assertThat(frames)
                .containsExactly(
                        "{\"type\":\"start\",\"messageId\":\"assistant-message-2\"}",
                        "{\"type\":\"data-persistence\",\"id\":\"11111111-1111-1111-1111-111111111111\",\"data\":{\"chatMessageId\":\"11111111-1111-1111-1111-111111111111\",\"state\":\"assistant-message-saved\"}}",
                        "{\"type\":\"finish\",\"finishReason\":\"tool-calls\"}",
                        "{\"type\":\"error\",\"errorText\":\"The assistant stream failed.\"}");
    }

    @Test
    void heartbeat_is_sse_comment_frame() {
        List<String> frames = new ArrayList<>();
        VercelProtocolEmitter emitter = new VercelProtocolEmitter(frames::add, testMapper());

        emitter.emitHeartbeat();

        assertThat(frames).containsExactly(": keepalive\n\n");
    }
}
