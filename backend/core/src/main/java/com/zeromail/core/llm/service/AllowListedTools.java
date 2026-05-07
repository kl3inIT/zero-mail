package com.zeromail.core.llm.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.zeromail.core.llm.model.LlmTool;

@Component
public class AllowListedTools {

    private static final List<LlmTool> ALLOW_LISTED = List.of(
            new LlmTool("label", "Apply a Gmail label to the email", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "value", Map.of("type", "string", "description", "Label name")),
                    "required", List.of("value"))),
            new LlmTool("archive", "Archive the email (skip inbox)", Map.of(
                    "type", "object",
                    "properties", Map.of())),
            new LlmTool("save_draft", "Save a draft reply for the email", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "body", Map.of("type", "string", "description", "Draft body")),
                    "required", List.of("body"))));

    public List<LlmTool> tools() {
        return ALLOW_LISTED;
    }
}
