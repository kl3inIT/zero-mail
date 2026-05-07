package com.zeromail.api.dto.llm;

import java.time.Instant;

public record ByokSaveResponse(boolean ok, Instant savedAt) {}
