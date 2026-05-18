package com.zeromail.core.chat.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class VercelProtocolEmitterTest {

    @Test
    void rejects_text_delta_before_text_start() {
        VercelProtocolEmitter emitter = new VercelProtocolEmitter(ignoredFrame -> {});

        assertThatThrownBy(() -> emitter.emitTextDelta("part-1", "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("text delta before text start");
    }

    @Test
    void emits_ordered_json_frames_for_text_and_tools() {
        List<String> frames = new ArrayList<>();
        VercelProtocolEmitter emitter =
                new VercelProtocolEmitter(frames::add, JsonMapper.builder().build());

        emitter.emitTextStart("part-1");
        emitter.emitTextDelta("part-1", "Xin");
        emitter.emitTextEnd("part-1");
        emitter.emitToolInputStart("call-1", "searchInbox");
        emitter.emitToolInputAvailable("call-1", "searchInbox", "{\"query\":\"Acme\"}");
        emitter.emitToolOutputAvailable("call-1", "{\"resultCount\":1}");
        emitter.emitFinish("stop");

        assertThat(frames)
                .containsExactly(
                        "{\"type\":\"text-start\",\"id\":\"part-1\"}",
                        "{\"type\":\"text-delta\",\"id\":\"part-1\",\"delta\":\"Xin\"}",
                        "{\"type\":\"text-end\",\"id\":\"part-1\"}",
                        "{\"type\":\"tool-input-start\",\"toolCallId\":\"call-1\",\"toolName\":\"searchInbox\"}",
                        "{\"type\":\"tool-input-available\",\"toolCallId\":\"call-1\",\"toolName\":\"searchInbox\",\"input\":\"{\\\"query\\\":\\\"Acme\\\"}\"}",
                        "{\"type\":\"tool-output-available\",\"toolCallId\":\"call-1\",\"output\":\"{\\\"resultCount\\\":1}\"}",
                        "{\"type\":\"finish\",\"reason\":\"stop\"}");
    }

    @Test
    void heartbeat_is_sse_comment_frame() {
        List<String> frames = new ArrayList<>();
        VercelProtocolEmitter emitter = new VercelProtocolEmitter(frames::add);

        emitter.emitHeartbeat();

        assertThat(frames).containsExactly(": keepalive\n\n");
    }
}
