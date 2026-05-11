package com.zeromail.core.llm.exception;

/**
 * Opaque semantic-intent evaluation failure. The cause is retained for operators, but the public
 * message never inherits provider-side text.
 */
public class LlmEvaluationFailedException extends RuntimeException {

    private static final String MESSAGE = "LLM semantic intent evaluation failed";

    public LlmEvaluationFailedException(Throwable cause) {
        super(MESSAGE, cause);
    }

    @Override
    public String getMessage() {
        return MESSAGE;
    }
}
