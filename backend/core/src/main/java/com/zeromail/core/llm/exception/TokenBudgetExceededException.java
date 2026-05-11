package com.zeromail.core.llm.exception;

/**
 * Raised before an LLM call when the estimated semantic-intent prompt would exceed the gateway's
 * token cap.
 */
public class TokenBudgetExceededException extends RuntimeException {

  public TokenBudgetExceededException(int estimatedTokens, int tokenCap) {
    super("Estimated prompt tokens %d exceed token cap %d".formatted(estimatedTokens, tokenCap));
  }
}
