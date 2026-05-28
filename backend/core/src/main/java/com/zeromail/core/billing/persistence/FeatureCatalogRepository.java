package com.zeromail.core.billing.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureCatalogRepository extends JpaRepository<FeatureCatalogEntity, String> {

    List<FeatureCatalogEntity> findByActiveTrueOrderByCategoryAscSortOrderAscCodeAsc();
}
