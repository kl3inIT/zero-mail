package com.zeromail.core.chat.usecases;

import reactor.core.Disposable;

/**
 * Spring-AI-free streaming boundary for the assistant chat path.
 *
 * <p>Implementations MAY persist user-typed chat configuration and structured tool I/O per the
 * project privacy carve-out, but MUST NOT log raw email-body prompts, completions, or tool payload
 * text. The provider flag that disables internal tool execution is load-bearing: every
 * implementation must keep tool execution in Zero Mail.
 */
@SuppressWarnings("unused")
public interface ChatLlmGateway {

    Disposable streamChat(ChatStreamRequest streamRequest, ChatStreamSink streamSink);
}
