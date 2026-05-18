package com.zeromail.core.chat.usecases.tools;

import com.zeromail.core.chat.domain.ChatToolName;

public interface ChatReadToolHandler {

    ChatToolName name();

    String executeJson(String inputJson, String tenantId);
}
