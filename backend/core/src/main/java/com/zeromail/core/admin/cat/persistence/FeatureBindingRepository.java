package com.zeromail.core.admin.cat.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureBindingRepository extends JpaRepository<FeatureBindingEntity, UUID> {}
