package com.zeromail.core.triage.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TenantSenderOptInRepository extends JpaRepository<TenantSenderOptInEntity, UUID> {

    boolean existsByTenantIdAndSenderEmail(UUID tenantId, String senderEmail);

    List<TenantSenderOptInEntity> findByTenantId(UUID tenantId);

    @Modifying
    @Transactional
    @Query(
            value =
                    """
          INSERT INTO tenant_sender_opt_in (
            id, tenant_id, sender_email, created_at, updated_at, version
          )
          VALUES (:id, :tenantId, :senderEmail, :createdAt, :createdAt, 0)
          ON CONFLICT (tenant_id, sender_email) DO NOTHING
          """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("senderEmail") String senderEmail,
            @Param("createdAt") Instant createdAt);
}
