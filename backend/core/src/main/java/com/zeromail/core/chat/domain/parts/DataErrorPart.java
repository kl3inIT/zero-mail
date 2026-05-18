package com.zeromail.core.chat.domain.parts;

public record DataErrorPart(String partId, String toolCallId, String toolName, String errorMessage)
        implements Part {}
