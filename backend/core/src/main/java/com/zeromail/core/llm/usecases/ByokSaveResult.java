package com.zeromail.core.llm.usecases;

import java.time.Instant;

public record ByokSaveResult(boolean ok, Instant savedAt) {}
