package com.zeromail.core.gmail.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Canonical Gmail "category" taxonomy as recognized by Gmail's inbox UI: Primary, Promotions,
 * Social, Updates, Forums.
 *
 * <p>Source of truth (REVIEW IN-03): the same five ids are referenced by {@link
 * com.zeromail.core.rules.service.ActionProposalMerger} (when normalizing category-vs-label
 * mismatch warnings), the rule-template catalog seed (Liquibase 022/023), and any future matcher
 * validator. Centralizing them here prevents the seed and the merger from drifting (the original
 * {@code pin-calendar} seed referenced {@code CATEGORY_PERSONAL}, an id not in this set, and the
 * conflict detector silently skipped it - see WR-03).
 *
 * <p>Implements {@link IdentifiedEnum} only - categories are unordered, so per project D-B5 we do
 * NOT extend {@code OrderedEnum}.
 */
public enum GmailCategory implements IdentifiedEnum {
    PRIMARY("primary"),
    PROMOTIONS("promotions"),
    SOCIAL("social"),
    UPDATES("updates"),
    FORUMS("forums");

    /**
     * Lower-case canonical ids, matching Gmail's label-id convention after the {@code CATEGORY_}
     * prefix is stripped (e.g. {@code CATEGORY_UPDATES} -> {@code updates}).
     */
    public static final Set<String> CANONICAL_IDS =
            Stream.of(values()).map(GmailCategory::id).collect(Collectors.toUnmodifiableSet());

    private final String id;

    GmailCategory(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    /**
     * Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id
     * (fail-loud).
     */
    public static GmailCategory fromId(String id) {
        return Stream.of(values())
                .filter(category -> category.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown GmailCategory id: " + id));
    }
}
