package com.zeromail.core.admin.cat.projection;

import java.util.List;

public record CatalogDiff(
        List<CatalogModelRow> added, List<CatalogModelRow> removed, List<CatalogModelRow> changed) {

    public static CatalogDiff empty() {
        return new CatalogDiff(List.of(), List.of(), List.of());
    }
}
