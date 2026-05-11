package com.zeromail.core.triage.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TenantProtectedSenderObservationRepository
    extends JpaRepository<TenantProtectedSenderObservationEntity, UUID> {

  Optional<TenantProtectedSenderObservationEntity> findByTenantIdAndSenderEmail(
      UUID tenantId, String senderEmail);

  List<TenantProtectedSenderObservationEntity> findByTenantId(UUID tenantId);

  @Modifying
  @Transactional
  @Query(
      value =
          """
          INSERT INTO tenant_protected_sender_observation AS protected_sender_observation (
            id,
            tenant_id,
            sender_email,
            first_observed_at,
            last_observed_at,
            observation_count,
            created_at,
            updated_at,
            version
          )
          VALUES (
            :id,
            :tenantId,
            :senderEmail,
            :observedAt,
            :observedAt,
            1,
            :observedAt,
            :observedAt,
            0
          )
          ON CONFLICT (tenant_id, sender_email)
          DO UPDATE SET
            last_observed_at = EXCLUDED.last_observed_at,
            observation_count = protected_sender_observation.observation_count + 1,
            updated_at = EXCLUDED.updated_at,
            version = protected_sender_observation.version + 1
          """,
      nativeQuery = true)
  int upsertObservation(
      @Param("id") UUID id,
      @Param("tenantId") UUID tenantId,
      @Param("senderEmail") String senderEmail,
      @Param("observedAt") Instant observedAt);
}
