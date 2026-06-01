package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.llm.domain.LlmProvider;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModelCatalogRepository extends JpaRepository<ModelCatalogEntity, String> {

    @Query(
            """
            select model
            from ModelCatalogEntity model
            where model.provider = :provider
              and model.deprecatedAt is null
            order by model.modelId
            """)
    List<ModelCatalogEntity> findEnabledModelsByProvider(@Param("provider") LlmProvider provider);
}
