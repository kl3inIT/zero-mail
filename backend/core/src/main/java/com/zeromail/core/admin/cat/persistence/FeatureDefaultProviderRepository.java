package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureDefaultProviderRepository
        extends JpaRepository<FeatureDefaultProviderEntity, FeatureDefaultProviderId> {

    /** All 3 tiers for one feature, ordered by tier weight (PRIMARY first). */
    @Query(
            "select fdp from FeatureDefaultProviderEntity fdp "
                    + "where fdp.feature = :feature "
                    + "order by fdp.tier")
    List<FeatureDefaultProviderEntity> findByFeatureOrderByTier(@Param("feature") Feature feature);

    /** Resolve the binding for a single (feature, tier) cell of the matrix. */
    default Optional<FeatureDefaultProviderEntity> findBinding(Feature feature, RoutingTier tier) {
        return findById(new FeatureDefaultProviderId(feature, tier));
    }

    /** Full 9-row matrix (3 features × 3 tiers). */
    @Query("select fdp from FeatureDefaultProviderEntity fdp " + "order by fdp.feature, fdp.tier")
    List<FeatureDefaultProviderEntity> findAllOrderedByFeatureAndTier();
}
