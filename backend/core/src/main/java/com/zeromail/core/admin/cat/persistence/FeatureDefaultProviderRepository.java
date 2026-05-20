package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureDefaultProviderRepository
        extends JpaRepository<FeatureDefaultProviderEntity, Feature> {}
