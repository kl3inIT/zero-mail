package com.zeromail.core.llm.application;

import java.time.Instant;

public record ByokSaveResult(boolean ok, Instant savedAt) {}
