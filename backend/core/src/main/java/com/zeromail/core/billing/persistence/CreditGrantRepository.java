package com.zeromail.core.billing.persistence;

import com.zeromail.core.billing.domain.CreditGrantCategory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditGrantRepository extends JpaRepository<CreditGrantEntity, UUID> {

    Optional<CreditGrantEntity> findByTenantIdAndCategoryAndRefTypeAndRefId(
            UUID tenantId, CreditGrantCategory category, String refType, String refId);
}
