package com.zeromail.core.billing.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Billable call-site identifier. Implements {@link IdentifiedEnum}; the integer payload that used
 * to live on each constant (the per-call credit cost) has been moved to the {@code feature_catalog}
 * Postgres table where it can be tuned without a redeploy. See {@code
 * com.zeromail.core.billing.usecases.FeatureCatalogCache} for the runtime lookup.
 *
 * <p><b>Membership:</b> {@code TRIAGE}, {@code DRAFT}, {@code PREVIEW}, {@code
 * TRIAGE_PLATFORM_LLM}, {@code TRIAGE_DETERMINISTIC} (D-G3 + Phase 4 D-E1), and {@code DIGEST} (the
 * weekly content-digest send-time summarizer). There is intentionally no BYOK member because BYOK
 * traffic bypasses the ledger entirely. The ledger service contract documents the BYOK skip path.
 *
 * <p><b>Cost source:</b> {@code feature_catalog.default_credit_cost} keyed by {@link #id()} (which
 * equals {@link #name()}). A {@code FeatureCatalogConsistencyChecker} startup validator enforces
 * that every constant here has a row in that table — so {@code
 * FeatureCatalogCache.defaultCost(...)} is total over this enum.
 */
public enum CallSite implements IdentifiedEnum {
    TRIAGE,
    DRAFT,
    PREVIEW,
    TRIAGE_PLATFORM_LLM,
    TRIAGE_DETERMINISTIC,
    DIGEST;

    @Override
    public String id() {
        return name();
    }

    public static CallSite fromId(String id) {
        return Stream.of(values())
                .filter(callSite -> callSite.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown CallSite id: " + id));
    }
}
