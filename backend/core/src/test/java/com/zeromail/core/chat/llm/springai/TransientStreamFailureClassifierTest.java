package com.zeromail.core.chat.llm.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.core.JsonValue;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/**
 * Pure-unit tests for {@link TransientStreamFailureClassifier}. No Spring context, no Postgres, no
 * real LLM. Verifies the exception-chain matching against the same shape produced by Spring AI +
 * OpenAI Java SDK + OkHttp against the 9router gateway.
 */
class TransientStreamFailureClassifierTest {

    @Test
    void openai_io_exception_is_transient() {
        OpenAIIoException openAiIoException = new OpenAIIoException("Request failed");
        assertThat(TransientStreamFailureClassifier.isTransient(openAiIoException)).isTrue();
    }

    @Test
    void interrupted_io_exception_is_transient() {
        InterruptedIOException interruptedIoException = new InterruptedIOException("timeout");
        assertThat(TransientStreamFailureClassifier.isTransient(interruptedIoException)).isTrue();
    }

    @Test
    void completion_exception_wrapping_openai_io_exception_is_transient() {
        // Matches the runtime shape the user pasted: CompletionException wraps the SDK exception
        // which wraps InterruptedIOException which wraps OkHttp's internal StreamResetException.
        InterruptedIOException interruptedIoException = new InterruptedIOException("timeout");
        OpenAIIoException openAiIoException =
                new OpenAIIoException("Request failed", interruptedIoException);
        CompletionException completionException = new CompletionException(openAiIoException);
        assertThat(TransientStreamFailureClassifier.isTransient(completionException)).isTrue();
    }

    @Test
    void openai_service_exception_is_not_transient() {
        // 4xx/5xx with structured body from the upstream LLM gateway. Must not auto-retry to
        // avoid silent credit inflation or repeated bad-request shapes. OpenAIServiceException is
        // abstract in the SDK and its concrete subclasses (BadRequestException, etc.) have private
        // constructors, so we extend it locally with a minimal stub purely for classifier input.
        OpenAIServiceException serviceException = new TestServiceException();
        assertThat(TransientStreamFailureClassifier.isTransient(serviceException)).isFalse();
    }

    @Test
    void plain_ioexception_is_not_transient() {
        // A bare IOException that is not InterruptedIOException is treated as non-transient. We
        // deliberately keep the classifier narrow so it only retries the precise shape we've
        // observed against 9router; anything broader risks silently retrying logical errors.
        IOException ioException = new IOException("connection refused");
        assertThat(TransientStreamFailureClassifier.isTransient(ioException)).isFalse();
    }

    @Test
    void null_input_is_not_transient() {
        assertThat(TransientStreamFailureClassifier.isTransient(null)).isFalse();
    }

    @Test
    void exception_chain_with_two_step_cycle_terminates() {
        // Java's Throwable.initCause rejects DIRECT self-cycles (cause == this) but NOT longer
        // chains: initCause only verifies the receiver doesn't already have a cause. Two
        // exceptions can therefore reference each other and produce an infinite walk if the
        // classifier doesn't keep a visited-set or hop counter.
        //
        // We can't construct the cycle via initCause (Throwable rejects the second call once
        // cause is set) and reflection into Throwable.cause is blocked under JDK 25 module
        // boundaries. Instead, a tiny CyclicThrowable subclass overrides getCause() to return
        // its sibling -- this is the exact "broken library" shape the classifier guards against.
        CyclicThrowable nodeA = new CyclicThrowable("a");
        CyclicThrowable nodeB = new CyclicThrowable("b");
        nodeA.linkTo(nodeB);
        nodeB.linkTo(nodeA);

        // Sanity: the cycle is real -- after an even number of hops we return to nodeA.
        Throwable cursor = nodeA;
        for (int hop = 0; hop < 4; hop++) {
            cursor = cursor.getCause();
        }
        assertThat(cursor).isSameAs(nodeA);

        // None of the cycle nodes are transient transport errors, so the classifier must
        // terminate via its hop-count guard and return false rather than loop forever.
        assertThat(TransientStreamFailureClassifier.isTransient(nodeA)).isFalse();
    }

    private static final class CyclicThrowable extends RuntimeException {
        private Throwable sibling;

        private CyclicThrowable(String message) {
            super(message);
        }

        private void linkTo(Throwable sibling) {
            this.sibling = sibling;
        }

        @Override
        public synchronized Throwable getCause() {
            return sibling;
        }
    }

    /**
     * Minimal concrete subclass of the abstract {@link OpenAIServiceException}. The SDK's real
     * subclasses ({@code BadRequestException}, {@code RateLimitException}, etc.) hide their
     * constructors behind generated builders, so we extend it directly here. Only used as input to
     * the classifier — none of the abstract accessors are read by {@link
     * TransientStreamFailureClassifier#isTransient}.
     */
    private static final class TestServiceException extends OpenAIServiceException {
        private TestServiceException() {
            super("400: bad request", null);
        }

        @Override
        public int statusCode() {
            return 400;
        }

        @Override
        public Headers headers() {
            return Headers.builder().build();
        }

        @Override
        public JsonValue body() {
            return JsonValue.from(Optional.empty());
        }

        @Override
        public Optional<String> code() {
            return Optional.empty();
        }

        @Override
        public Optional<String> param() {
            return Optional.empty();
        }

        @Override
        public Optional<String> type() {
            return Optional.empty();
        }
    }
}
