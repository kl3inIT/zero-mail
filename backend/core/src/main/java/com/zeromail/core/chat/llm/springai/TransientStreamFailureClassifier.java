package com.zeromail.core.chat.llm.springai;

import com.openai.errors.OpenAIIoException;
import java.io.InterruptedIOException;
import java.util.Set;

/**
 * Classifies a chat streaming subscription failure as a transient I/O cancel that the orchestrator
 * may retry exactly once per turn.
 *
 * <p>Background: the chat streaming path runs through Spring AI's {@code OpenAiChatModel}, which
 * delegates to the OpenAI Java SDK's OkHttp transport. When the upstream LLM gateway at {@code
 * https://9router.zeromail.vn/v1} resets an in-flight HTTP/2 stream (server-initiated {@code
 * RST_STREAM(CANCEL)} on multi-step tool turns, observed against single-step calls), OkHttp
 * surfaces it as {@code java.io.InterruptedIOException: timeout} from {@code RealCall.timeoutExit}
 * whose own cause is {@code okhttp3.internal.http2.StreamResetException: stream was reset: CANCEL}.
 * The OpenAI SDK then wraps that in {@code OpenAIIoException("Request failed", ...)} and the
 * Reactor pipeline finally wraps the whole thing in {@code CompletionException}.
 *
 * <p>The classifier walks the cause chain and returns {@code true} when any link is one of the
 * known transient transport-level types. It deliberately does NOT classify {@link
 * com.openai.errors.OpenAIServiceException} (4xx/5xx with structured body) as transient — those are
 * logical errors from the LLM gateway and must not auto-retry to avoid silent credit inflation or
 * repeated bad-request shapes.
 *
 * <p>OkHttp's {@code StreamResetException} lives in an {@code internal} package; we match it by
 * fully qualified class name rather than importing the type, which keeps production code free of
 * {@code internal} package usage while still detecting the case deterministically.
 */
final class TransientStreamFailureClassifier {

    private static final String OKHTTP_STREAM_RESET_EXCEPTION_FQN =
            "okhttp3.internal.http2.StreamResetException";

    private static final Set<String> TRANSIENT_FULLY_QUALIFIED_NAMES =
            Set.of(OKHTTP_STREAM_RESET_EXCEPTION_FQN);

    private TransientStreamFailureClassifier() {}

    /**
     * Returns {@code true} when the cause chain contains an HTTP/2 stream cancel, an interrupted
     * I/O read, or an {@link OpenAIIoException} (the SDK's "Request failed" wrapper). Returns
     * {@code false} for logical service errors or null input.
     */
    static boolean isTransient(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        Throwable cursor = throwable;
        int guard = 0;
        while (cursor != null && guard < 16) {
            if (matches(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
            guard++;
        }
        return false;
    }

    private static boolean matches(Throwable candidate) {
        if (candidate instanceof OpenAIIoException) {
            return true;
        }
        if (candidate instanceof InterruptedIOException) {
            return true;
        }
        return TRANSIENT_FULLY_QUALIFIED_NAMES.contains(candidate.getClass().getName());
    }
}
