package com.zeromail.core.llm.model;

import java.time.Instant;

public record ByokSaveResult(boolean ok, Instant savedAt) {}
