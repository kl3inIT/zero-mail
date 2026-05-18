package com.zeromail.core.chat.usecases;

import java.util.UUID;

public interface ChatStreamSink {

    void emitTextStart(String partId);

    void emitTextDelta(String partId, String tokenText);

    void emitTextEnd(String partId);

    void emitToolInputStart(String toolCallId, String toolName);

    void emitToolInputAvailable(String toolCallId, String toolName, String inputJson);

    void emitToolOutputAvailable(String toolCallId, String outputJson);

    void emitDataPersistence(UUID chatMessageId, String state);

    void emitFinish(String reason);

    void emitError(String code, String userFacingMessage);

    void emitHeartbeat();
}
