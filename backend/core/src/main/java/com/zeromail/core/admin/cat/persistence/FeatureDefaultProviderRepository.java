package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureDefaultProviderRepository
        extends JpaRepository<FeatureDefaultProviderEntity, FeatureDefaultProviderId> {

    /** Every tier binding for one feature, unordered. Callers sort by tier weight. */
    @Query("select fdp from FeatureDefaultProviderEntity fdp where fdp.feature = :feature")
    List<FeatureDefaultProviderEntity> findByFeature(@Param("feature") Feature feature);

    /**
     * All tiers for one feature in failover order (PRIMARY → FALLBACK → LAST_RESORT).
     *
     * <p>The {@code tier} column is {@code @Enumerated(STRING)}, so a SQL {@code order by tier}
     * sorts the enum <em>names</em> lexically — {@code FALLBACK < LAST_RESORT < PRIMARY} — which
     * inverts the failover chain and makes the FALLBACK provider be tried before PRIMARY. We sort
     * by {@link RoutingTier#weight()} in-app instead, keeping the enum as the single source of
     * truth for tier ordering.
     */
    default List<FeatureDefaultProviderEntity> findByFeatureOrderByTier(Feature feature) {
        return findByFeature(feature).stream()
                .sorted(
                        Comparator.comparingInt(
                                (FeatureDefaultProviderEntity binding) ->
                                        binding.getTier().weight()))
                .toList();
    }

    /** Resolve the binding for a single (feature, tier) cell of the matrix. */
    default Optional<FeatureDefaultProviderEntity> findBinding(Feature feature, RoutingTier tier) {
        return findById(new FeatureDefaultProviderId(feature, tier));
    }

    /**
     * Full matrix (every feature × tier), ordered by feature name then tier weight. Sorting by
     * {@link RoutingTier#weight()} (not the lexical {@code order by tier}) keeps PRIMARY above
     * FALLBACK in the matrix — see {@link #findByFeatureOrderByTier(Feature)}.
     */
    default List<FeatureDefaultProviderEntity> findAllOrderedByFeatureAndTier() {
        return findAll().stream()
                .sorted(
                        Comparator.comparing(
                                        (FeatureDefaultProviderEntity binding) ->
                                                binding.getFeature().name())
                                .thenComparingInt(binding -> binding.getTier().weight()))
                .toList();
    }
}
