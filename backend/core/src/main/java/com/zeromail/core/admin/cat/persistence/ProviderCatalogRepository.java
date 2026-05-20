package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderCatalogRepository
        extends JpaRepository<ProviderCatalogEntity, LlmProvider> {}
