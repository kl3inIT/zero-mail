package com.zeromail.core.admin.cat.persistence.lowlevel;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC writes + per-provider reads against {@code model_catalog}, {@code feature_binding}, {@code
 * provider_catalog}, and {@code assistant_settings} (pinned-tenant count) for the catalog admin use
 * case.
 */
@Repository
public class ModelCatalogWriteRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelCatalogWriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public List<ProviderModelRow> findProviderModelRows(LlmProvider provider) {
        return jdbcTemplate.query(
                """
                SELECT binding.feature,
                       model.provider,
                       model.model_id,
                       model.display_name,
                       (defaults.model_id = model.model_id AND defaults.feature = binding.feature) AS default_model,
                       model.is_recommended,
                       model.cost_per_1k_input,
                       model.cost_per_1k_output,
                       model.deprecated_at,
                       (
                         SELECT COUNT(DISTINCT settings.tenant_id)
                         FROM assistant_settings settings
                         WHERE settings.chat_model_id = model.model_id
                            OR settings.triage_model_id = model.model_id
                            OR settings.draft_model_id = model.model_id
                       ) AS pinned_tenant_count
                FROM model_catalog model
                JOIN feature_binding binding ON binding.model_id = model.model_id
                LEFT JOIN feature_default_provider defaults ON defaults.feature = binding.feature
                WHERE model.provider = ?
                ORDER BY binding.feature, default_model DESC, model.is_recommended DESC, model.model_id
                """,
                ModelCatalogWriteRepository::mapProviderModelRow,
                provider.id());
    }

    public void insertManualModel(
            LlmProvider provider,
            String modelId,
            String displayName,
            BigDecimal costPer1kInput,
            BigDecimal costPer1kOutput,
            boolean recommended) {
        jdbcTemplate.update(
                """
                INSERT INTO model_catalog(
                    model_id, provider, display_name, cost_per_1k_input, cost_per_1k_output,
                    is_recommended, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """,
                modelId,
                provider.id(),
                displayName,
                costPer1kInput,
                costPer1kOutput,
                recommended);
    }

    public void upsertFetchedModel(LlmProvider provider, String modelId, String displayName) {
        jdbcTemplate.update(
                """
                INSERT INTO model_catalog(model_id, provider, display_name, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (model_id) DO UPDATE
                SET display_name = EXCLUDED.display_name,
                    deprecated_at = NULL,
                    updated_at = NOW()
                """,
                modelId,
                provider.id(),
                displayName);
    }

    public int softDeleteModel(String modelId) {
        return jdbcTemplate.update(
                """
                UPDATE model_catalog
                SET deprecated_at = COALESCE(deprecated_at, NOW()),
                    updated_at = NOW()
                WHERE model_id = ?
                """,
                modelId);
    }

    public void upsertFeatureBinding(String modelId, Feature feature) {
        jdbcTemplate.update(
                """
                INSERT INTO feature_binding(model_id, feature)
                VALUES (?, ?)
                ON CONFLICT (model_id, feature) DO UPDATE SET enabled = TRUE
                """,
                modelId,
                feature.id());
    }

    public long bumpProviderCatalogVersion(LlmProvider provider) {
        Long catalogVersion =
                jdbcTemplate.queryForObject(
                        """
                        UPDATE provider_catalog
                        SET catalog_version = catalog_version + 1
                        WHERE provider = ?
                        RETURNING catalog_version
                        """,
                        Long.class,
                        provider.id());
        return catalogVersion == null ? 1L : catalogVersion;
    }

    public long countPinnedTenants(String modelId) {
        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(DISTINCT tenant_id)
                        FROM assistant_settings
                        WHERE chat_model_id = ?
                           OR triage_model_id = ?
                           OR draft_model_id = ?
                        """,
                        Long.class,
                        modelId,
                        modelId,
                        modelId);
        return count == null ? 0L : count;
    }

    public Optional<LlmProvider> findProviderForModel(String modelId) {
        return jdbcTemplate
                .query(
                        "SELECT provider FROM model_catalog WHERE model_id = ?",
                        (resultSet, _) -> LlmProvider.fromId(resultSet.getString("provider")),
                        modelId)
                .stream()
                .findFirst();
    }

    public Optional<String> findActiveProviderForModel(String modelId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT provider
                        FROM model_catalog
                        WHERE model_id = ?
                          AND deprecated_at IS NULL
                        """,
                        (resultSet, _) -> resultSet.getString("provider"),
                        modelId)
                .stream()
                .findFirst();
    }

    public Optional<String> findFirstRecommendedModelId(LlmProvider provider, Feature feature) {
        return jdbcTemplate
                .query(
                        """
                        SELECT model.model_id
                        FROM model_catalog model
                        JOIN feature_binding binding ON binding.model_id = model.model_id
                        WHERE model.provider = ?
                          AND binding.feature = ?
                          AND binding.enabled = TRUE
                          AND model.deprecated_at IS NULL
                        ORDER BY model.is_recommended DESC, model.model_id
                        LIMIT 1
                        """,
                        (resultSet, _) -> resultSet.getString("model_id"),
                        provider.id(),
                        feature.id())
                .stream()
                .findFirst();
    }

    /** Clear a single tier slot. The router will skip the tier on the next walk. */
    public int deleteFeatureDefaultTier(Feature feature, String tierId) {
        return jdbcTemplate.update(
                "DELETE FROM feature_default_provider WHERE feature = ? AND tier = ?",
                feature.id(),
                tierId);
    }

    public java.util.Map<String, String> findActiveModelDisplayNames(LlmProvider provider) {
        java.util.Map<String, String> displayNamesById = new java.util.HashMap<>();
        org.springframework.jdbc.core.RowCallbackHandler rowCallbackHandler =
                resultSet ->
                        displayNamesById.put(
                                resultSet.getString("model_id"),
                                resultSet.getString("display_name"));
        jdbcTemplate.query(
                """
                SELECT model_id, display_name
                FROM model_catalog
                WHERE provider = ?
                  AND deprecated_at IS NULL
                """,
                rowCallbackHandler,
                provider.id());
        return displayNamesById;
    }

    public long bumpProviderCatalogVersionById(String providerId) {
        Long catalogVersion =
                jdbcTemplate.queryForObject(
                        """
                        UPDATE provider_catalog
                        SET catalog_version = catalog_version + 1
                        WHERE provider = ?
                        RETURNING catalog_version
                        """,
                        Long.class,
                        providerId);
        return catalogVersion == null ? 1L : catalogVersion;
    }

    private static ProviderModelRow mapProviderModelRow(ResultSet resultSet, int rowNumber)
            throws SQLException {
        Feature feature = Feature.fromId(resultSet.getString("feature"));
        CatalogModelRow catalogModelRow =
                new CatalogModelRow(
                        resultSet.getString("provider"),
                        resultSet.getString("model_id"),
                        resultSet.getString("display_name"),
                        resultSet.getBoolean("default_model"),
                        resultSet.getBoolean("is_recommended"),
                        resultSet.getBigDecimal("cost_per_1k_input"),
                        resultSet.getBigDecimal("cost_per_1k_output"),
                        nullableInstant(resultSet, "deprecated_at"),
                        resultSet.getLong("pinned_tenant_count"));
        return new ProviderModelRow(feature, catalogModelRow);
    }

    private static Instant nullableInstant(ResultSet resultSet, String columnName)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record ProviderModelRow(Feature feature, CatalogModelRow catalogModelRow) {}
}
