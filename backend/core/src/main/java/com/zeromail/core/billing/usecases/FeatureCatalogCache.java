package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.persistence.FeatureCatalogEntity;
import com.zeromail.core.billing.persistence.FeatureCatalogRepository;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory cache of {@code feature_catalog} rows keyed by code. Also acts as the startup
 * consistency checker: at boot, asserts that every {@link CallSite} enum value has a corresponding
 * row in the table — drift would silently route credit-cost lookups to a missing row and break
 * reserve().
 *
 * <p>Reload is exposed via {@link #refresh()} so the admin permission-management UI (separate
 * phase) can invalidate the cache after editing a row. v1 has no admin UI, so the cache is
 * effectively read-only after startup.
 *
 * <p>The cache is intentionally simple: 5-10 catalog rows tops, hot read path, no need for an
 * external cache server. {@link ConcurrentHashMap} gives lock-free reads.
 */
@Component
public class FeatureCatalogCache {

    private static final Logger log = LoggerFactory.getLogger(FeatureCatalogCache.class);

    private final FeatureCatalogRepository featureCatalogRepository;
    private final Map<String, FeatureCatalogEntity> rowsByCode = new ConcurrentHashMap<>();

    public FeatureCatalogCache(FeatureCatalogRepository featureCatalogRepository) {
        this.featureCatalogRepository = featureCatalogRepository;
    }

    @PostConstruct
    void initialize() {
        loadAndValidate();
    }

    /**
     * Reload all rows from the database and re-run the consistency check. Intended for the admin UI
     * to call after writing a row. Safe to call from any thread; the swap is atomic per key.
     */
    public synchronized void refresh() {
        loadAndValidate();
    }

    /**
     * Resolve the default credit cost for the given call site. Total over {@link CallSite} —
     * startup validation guarantees no missing entry.
     */
    public int defaultCost(CallSite callSite) {
        FeatureCatalogEntity row = rowsByCode.get(callSite.id());
        if (row == null) {
            throw new IllegalStateException(
                    "feature_catalog row missing for CallSite "
                            + callSite
                            + " — startup validation should have caught this; was the cache"
                            + " populated?");
        }
        return row.getDefaultCreditCost();
    }

    /** True iff the global active flag is set on the row. Useful for admin kill-switch checks. */
    public boolean isActive(CallSite callSite) {
        FeatureCatalogEntity row = rowsByCode.get(callSite.id());
        return row != null && row.isActive();
    }

    private void loadAndValidate() {
        List<FeatureCatalogEntity> rows = featureCatalogRepository.findAll();
        Map<String, FeatureCatalogEntity> freshRowsByCode =
                rows.stream()
                        .collect(
                                Collectors.toMap(
                                        FeatureCatalogEntity::getCode, row -> row, (a, b) -> a));

        Set<String> enumCodes =
                Arrays.stream(CallSite.values()).map(CallSite::id).collect(Collectors.toSet());
        Set<String> missingInDb = new HashSet<>(enumCodes);
        missingInDb.removeAll(freshRowsByCode.keySet());
        if (!missingInDb.isEmpty()) {
            throw new IllegalStateException(
                    "feature_catalog rows missing for CallSite values: "
                            + missingInDb
                            + " — add a Liquibase seed row before deploying. Existing codes in DB:"
                            + " "
                            + freshRowsByCode.keySet());
        }

        Set<String> extraInDb = new HashSet<>(freshRowsByCode.keySet());
        extraInDb.removeAll(enumCodes);
        if (!extraInDb.isEmpty()) {
            log.warn(
                    "event=feature_catalog_extra_rows codes={} note=non-fatal_db_row_without_enum_value",
                    extraInDb);
        }

        rowsByCode.clear();
        rowsByCode.putAll(freshRowsByCode);

        log.info(
                "event=feature_catalog_cache_loaded row_count={} enum_count={}",
                freshRowsByCode.size(),
                enumCodes.size());
    }
}
