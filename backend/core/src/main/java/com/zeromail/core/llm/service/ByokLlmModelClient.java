package com.zeromail.core.llm.service;

import com.zeromail.core.llm.usecases.LlmChatRequest;
import com.zeromail.core.llm.usecases.LlmChatResult;

/**
 * Pure-Java seam for per-call BYOK model clients.
 *
 * <p>Implementations live in {@code core.llm.gateway.springai}. This differs from {@link
 * LlmModelClient}: platform calls carry no per-request secret, while BYOK calls receive the
 * decrypted key and endpoint for exactly one outbound request.
 */
public interface ByokLlmModelClient {

    LlmChatResult call(byte[] decryptedKey, String endpoint, LlmChatRequest request);
}
