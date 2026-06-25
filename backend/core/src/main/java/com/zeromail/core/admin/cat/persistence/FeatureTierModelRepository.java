package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureTierModelRepository
        extends JpaRepository<FeatureTierModelEntity, FeatureTierModelId> {

    /** Ordered model slots for one tier of one feature, position ascending (1 first). */
    @Query(
            "select row from FeatureTierModelEntity row "
                    + "where row.feature = :feature and row.tier = :tier "
                    + "order by row.position")
    List<FeatureTierModelEntity> findByFeatureAndTierOrderByPosition(
            @Param("feature") Feature feature, @Param("tier") RoutingTier tier);

    /** Every model slot for one feature across all tiers, unordered. Callers sort. */
    @Query("select row from FeatureTierModelEntity row where row.feature = :feature")
    List<FeatureTierModelEntity> findByFeature(@Param("feature") Feature feature);

    /**
     * Every slot for one feature across all tiers, grouped by tier weight (PRIMARY → FALLBACK →
     * LAST_RESORT) then by position ascending.
     *
     * <p>{@code tier} is {@code @Enumerated(STRING)}; a SQL {@code order by tier} sorts enum names
     * lexically (FALLBACK before PRIMARY), so we sort by {@link RoutingTier#weight()} in-app — see
     * {@link FeatureDefaultProviderRepository#findByFeatureOrderByTier(Feature)}.
     */
    default List<FeatureTierModelEntity> findByFeatureOrderByTierAndPosition(Feature feature) {
        return findByFeature(feature).stream()
                .sorted(
                        Comparator.comparingInt(
                                        (FeatureTierModelEntity row) -> row.getTier().weight())
                                .thenComparingInt(FeatureTierModelEntity::getPosition))
                .toList();
    }

    /**
     * Clears every slot for a tier so the service can rewrite the list atomically. Returns the row
     * count that was removed.
     */
    @Modifying
    @Query(
            "delete from FeatureTierModelEntity row "
                    + "where row.feature = :feature and row.tier = :tier")
    int deleteByFeatureAndTier(@Param("feature") Feature feature, @Param("tier") RoutingTier tier);
}
