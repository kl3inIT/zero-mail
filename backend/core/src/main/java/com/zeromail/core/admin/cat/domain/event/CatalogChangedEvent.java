package com.zeromail.core.admin.cat.domain.event;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CatalogChangedEvent(
        LlmProvider provider,
        List<String> affectedModelIds,
        Set<Feature> affectedFeatures,
        Instant occurredAt,
        long newCatalogVersion) {}
