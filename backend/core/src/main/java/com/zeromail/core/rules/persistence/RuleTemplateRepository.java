package com.zeromail.core.rules.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleTemplateRepository extends JpaRepository<RuleTemplateEntity, UUID> {

    Optional<RuleTemplateEntity> findByTemplateKeyAndTemplateVersion(
            String templateKey, int templateVersion);

    @Query(
            """
      SELECT ruleTemplateEntity
      FROM RuleTemplateEntity ruleTemplateEntity
      WHERE ruleTemplateEntity.statusId = :statusId
      ORDER BY ruleTemplateEntity.templateKey ASC, ruleTemplateEntity.templateVersion ASC
      """)
    List<RuleTemplateEntity> findByStatusIdOrderByTemplateKeyAscTemplateVersionAsc(
            @Param("statusId") String statusId);

    @Query(
            """
      SELECT ruleTemplateEntity
      FROM RuleTemplateEntity ruleTemplateEntity
      WHERE ruleTemplateEntity.statusId IN :statusIds
      ORDER BY ruleTemplateEntity.templateKey ASC, ruleTemplateEntity.templateVersion DESC
      """)
    List<RuleTemplateEntity> findByStatusIdsOrderByTemplateKeyAscTemplateVersionDesc(
            @Param("statusIds") List<String> statusIds);

    @Query(
            """
      SELECT ruleTemplateEntity
      FROM RuleTemplateEntity ruleTemplateEntity
      WHERE ruleTemplateEntity.templateKey = :templateKey
        AND ruleTemplateEntity.statusId IN :statusIds
      ORDER BY ruleTemplateEntity.templateVersion DESC
      """)
    List<RuleTemplateEntity> findByTemplateKeyAndStatusIdsOrderByTemplateVersionDesc(
            @Param("templateKey") String templateKey, @Param("statusIds") List<String> statusIds);
}
