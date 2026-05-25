package com.zeromail.core.admin.cat.persistence.lowlevel;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProviderCatalogWriteRepository {

    public static final String KIND_SPRING_AI_BUILT_IN = "SPRING_AI_BUILT_IN";
    public static final String KIND_COMPATIBLE_GATEWAY = "COMPATIBLE_GATEWAY";

    private final JdbcTemplate jdbcTemplate;

    public ProviderCatalogWriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public boolean exists(LlmProvider provider) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM provider_catalog WHERE provider = ?",
                        Integer.class,
                        provider.id());
        return count != null && count > 0;
    }

    public void insertCompatibleGateway(
            LlmProvider provider,
            String displayName,
            KeyFormat compatibleType,
            String defaultBaseUrl) {
        jdbcTemplate.update(
                """
        INSERT INTO provider_catalog(
            provider, enabled, display_name, provider_kind, compatible_type,
            default_base_url, catalog_version, created_at
        )
        VALUES (?, TRUE, ?, ?, ?, ?, 1, NOW())
        """,
                provider.id(),
                displayName,
                KIND_COMPATIBLE_GATEWAY,
                compatibleType.id(),
                defaultBaseUrl);
    }

    public ProviderDeleteCandidate findDeleteCandidateOrNull(LlmProvider provider) {
        return jdbcTemplate
                .query(
                        """
            SELECT provider, display_name, provider_kind, compatible_type
            FROM provider_catalog
            WHERE provider = ?
            """,
                        (resultSet, _) ->
                                new ProviderDeleteCandidate(
                                        LlmProvider.fromId(resultSet.getString("provider")),
                                        resultSet.getString("display_name"),
                                        resultSet.getString("provider_kind"),
                                        resultSet.getString("compatible_type")),
                        provider.id())
                .stream()
                .findFirst()
                .orElse(null);
    }

    public long countRoutingReferences(LlmProvider provider) {
        Long count =
                jdbcTemplate.queryForObject(
                        """
            SELECT
              (SELECT COUNT(*) FROM feature_default_provider WHERE provider = ?)
              +
              (
                SELECT COUNT(*)
                FROM feature_tier_model tier_model
                JOIN model_catalog model ON model.model_id = tier_model.model_id
                WHERE model.provider = ?
              )
            """,
                        Long.class,
                        provider.id(),
                        provider.id());
        return count == null ? 0L : count;
    }

    public long countPinnedTenants(LlmProvider provider) {
        Long count =
                jdbcTemplate.queryForObject(
                        """
            SELECT COUNT(DISTINCT settings.tenant_id)
            FROM assistant_settings settings
            JOIN model_catalog model
              ON model.model_id = settings.chat_model_id
              OR model.model_id = settings.triage_model_id
              OR model.model_id = settings.draft_model_id
            WHERE model.provider = ?
            """,
                        Long.class,
                        provider.id());
        return count == null ? 0L : count;
    }

    public ProviderDeleteResult deleteCompatibleGateway(LlmProvider provider) {
        int deletedKeys =
                jdbcTemplate.update(
                        "DELETE FROM llm_provider_master_key WHERE provider = ?", provider.id());
        int deletedModels =
                jdbcTemplate.update("DELETE FROM model_catalog WHERE provider = ?", provider.id());
        int deletedProviders =
                jdbcTemplate.update(
                        """
            DELETE FROM provider_catalog
            WHERE provider = ?
              AND provider_kind = ?
            """,
                        provider.id(),
                        KIND_COMPATIBLE_GATEWAY);
        return new ProviderDeleteResult(deletedProviders, deletedModels, deletedKeys);
    }

    public record ProviderDeleteCandidate(
            LlmProvider provider, String displayName, String providerKind, String compatibleType) {}

    public record ProviderDeleteResult(int deletedProviders, int deletedModels, int deletedKeys) {}
}
