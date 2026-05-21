package com.zeromail.core.chat.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.usecases.RawToolCall;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatToolCallRegistryIT {

    @Test
    void assembles_interleaved_tool_argument_deltas_by_tool_call_id() {
        ChatToolCallRegistry registry = new ChatToolCallRegistry();

        registry.appendDelta("call-1", "searchInbox", "{\"query\"");
        registry.appendDelta("call-2", "getRule", "{\"ruleId\":\"");
        registry.appendDelta("call-1", "searchInbox", ":\"Acme\"");
        registry.appendDelta("call-2", "getRule", "rule-123\"}");
        registry.appendDelta("call-1", "searchInbox", "}");

        List<RawToolCall> finalizedToolCalls = registry.finalizeToolCalls();

        assertThat(finalizedToolCalls).hasSize(2);
        assertThat(finalizedToolCalls.getFirst().toolCallId()).isEqualTo("call-1");
        assertThat(finalizedToolCalls.getFirst().toolName()).isEqualTo("searchInbox");
        assertThat(finalizedToolCalls.getFirst().argsJson()).isEqualTo("{\"query\":\"Acme\"}");
        assertThat(finalizedToolCalls.getFirst().finalized()).isTrue();
        assertThat(finalizedToolCalls.get(1).toolCallId()).isEqualTo("call-2");
        assertThat(finalizedToolCalls.get(1).argsJson()).isEqualTo("{\"ruleId\":\"rule-123\"}");
    }
}
