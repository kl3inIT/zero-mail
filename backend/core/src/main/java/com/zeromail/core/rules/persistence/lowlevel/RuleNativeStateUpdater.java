package com.zeromail.core.rules.persistence.lowlevel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.zeromail.core.rules.persistence.RuleEntity;

import jakarta.persistence.EntityManager;

@Repository
public class RuleNativeStateUpdater {

  private final EntityManager entityManager;

  public RuleNativeStateUpdater(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public boolean markPreviewSucceeded(
      UUID tenantId, UUID ruleId, Integer entityVersion, Instant previewedAt) {
    int updatedRows =
        entityManager
            .createNativeQuery(
                """
                update rules
                set last_previewed_entity_version = ?,
                    last_previewed_at = ?,
                    updated_at = now()
                where tenant_id = ?
                  and id = ?
                  and version = ?
                """)
            .setParameter(1, entityVersion)
            .setParameter(2, Timestamp.from(previewedAt))
            .setParameter(3, tenantId)
            .setParameter(4, ruleId)
            .setParameter(5, entityVersion)
            .executeUpdate();
    return updatedRows == 1;
  }

  public boolean updateEnabled(UUID tenantId, UUID ruleId, boolean enabled, Integer entityVersion) {
    int updatedRows =
        entityManager
            .createNativeQuery(
                """
                update rules
                set enabled = ?,
                    updated_at = now()
                where tenant_id = ?
                  and id = ?
                  and version = ?
                """)
            .setParameter(1, enabled)
            .setParameter(2, tenantId)
            .setParameter(3, ruleId)
            .setParameter(4, entityVersion)
            .executeUpdate();
    return updatedRows == 1;
  }

  public void refresh(RuleEntity ruleEntity) {
    entityManager.refresh(ruleEntity);
  }
}
