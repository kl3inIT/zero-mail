package com.zeromail.core.admin.cat.projection;

import com.zeromail.core.admin.cat.domain.Feature;
import java.util.List;

public record PerFeatureCatalog(
        Feature feature, List<CatalogModelRow> models, String defaultModelId) {}
