package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
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

    /** Every slot for one feature across all tiers, grouped first by tier then by position. */
    @Query(
            "select row from FeatureTierModelEntity row "
                    + "where row.feature = :feature "
                    + "order by row.tier, row.position")
    List<FeatureTierModelEntity> findByFeatureOrderByTierAndPosition(
            @Param("feature") Feature feature);

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
