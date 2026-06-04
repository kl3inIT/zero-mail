package com.zeromail.core.llm.usecases;

/**
 * One-line summary produced for a {@link DigestSummarySource}. The {@code ref} matches the source's
 * {@code ref}; sources the model did not return a parseable line for simply have no entry.
 */
public record DigestSummaryLine(String ref, String summary) {}
