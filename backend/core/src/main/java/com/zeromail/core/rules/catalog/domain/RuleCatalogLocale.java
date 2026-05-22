package com.zeromail.core.rules.catalog.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum RuleCatalogLocale implements IdentifiedEnum {
    ENGLISH("en"),
    VIETNAMESE("vi");

    private final String id;

    RuleCatalogLocale(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public static RuleCatalogLocale fromId(String id) {
        return Stream.of(values())
                .filter(ruleCatalogLocale -> ruleCatalogLocale.id.equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown RuleCatalogLocale id: " + id));
    }
}
