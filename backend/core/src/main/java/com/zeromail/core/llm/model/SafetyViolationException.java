package com.zeromail.core.llm.model;

/**
 * Thrown when an LLM response violates the gateway safety contract.
 *
 * <p><b>Privacy invariant:</b> handlers must never log this exception message or expose it to
 * clients. The message constructor exists only so tests can prove handler logging stays
 * metadata-only even if a future caller supplies a diagnostic.
 */
public class SafetyViolationException extends RuntimeException {

  public SafetyViolationException() {
    super();
  }

  public SafetyViolationException(String diagnosticMessage) {
    super(diagnosticMessage);
  }
}
