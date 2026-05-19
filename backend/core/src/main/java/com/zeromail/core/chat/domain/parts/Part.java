package com.zeromail.core.chat.domain.parts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextPart.class, name = "text"),
    @JsonSubTypes.Type(value = AssistantTextPart.class, name = "assistant-text"),
    @JsonSubTypes.Type(value = ToolCallPart.class, name = "tool-call"),
    @JsonSubTypes.Type(value = ToolOutputPart.class, name = "tool-output"),
    @JsonSubTypes.Type(value = DataErrorPart.class, name = "data-error")
})
public sealed interface Part
        permits TextPart, AssistantTextPart, ToolCallPart, ToolOutputPart, DataErrorPart {}
