package com.zeromail.core.admin.cat.projection;

import com.zeromail.core.llm.domain.LlmProvider;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record CatalogSyncJob(
        UUID jobId,
        LlmProvider provider,
        String status,
        Instant createdAt,
        Instant lastUpdatedAt,
        JsonNode stepStateJson) {}
