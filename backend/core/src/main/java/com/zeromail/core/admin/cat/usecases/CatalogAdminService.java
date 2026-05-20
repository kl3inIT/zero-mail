package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.event.CatalogChangedEvent;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import com.zeromail.core.admin.cat.projection.PerFeatureCatalog;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final ModelSchemaValidator modelSchemaValidator;
    private final AdminAuditWriter adminAuditWriter;
    private final FeatureDefaultProviderService featureDefaultProviderService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public CatalogAdminService(
            JdbcTemplate jdbcTemplate,
            ModelSchemaValidator modelSchemaValidator,
            AdminAuditWriter adminAuditWriter,
            FeatureDefaultProviderService featureDefaultProviderService,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.modelSchemaValidator =
                Objects.requireNonNull(modelSchemaValidator, "modelSchemaValidator");
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
        this.featureDefaultProviderService =
                Objects.requireNonNull(
                        featureDefaultProviderService, "featureDefaultProviderService");
        this.applicationEventPublisher =
                Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public Map<Feature, PerFeatureCatalog> listProvider(LlmProvider provider) {
        List<ProviderModelRow> rows =
                jdbcTemplate.query(
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
                        this::mapProviderModelRow,
                        provider.id());
        EnumMap<Feature, List<CatalogModelRow>> rowsByFeature = new EnumMap<>(Feature.class);
        EnumMap<Feature, String> defaultByFeature = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            rowsByFeature.put(feature, new ArrayList<>());
        }
        for (ProviderModelRow row : rows) {
            rowsByFeature.get(row.feature()).add(row.catalogModelRow());
            if (row.catalogModelRow().defaultModel()) {
                defaultByFeature.put(row.feature(), row.catalogModelRow().modelId());
            }
        }
        LinkedHashMap<Feature, PerFeatureCatalog> catalog = new LinkedHashMap<>();
        for (Feature feature : Feature.values()) {
            catalog.put(
                    feature,
                    new PerFeatureCatalog(
                            feature, rowsByFeature.get(feature), defaultByFeature.get(feature)));
        }
        return catalog;
    }

    @Transactional
    public void createManualModel(
            LlmProvider provider,
            String modelId,
            String displayName,
            BigDecimal costPer1kInput,
            BigDecimal costPer1kOutput,
            boolean recommended,
            String requestIp,
            UUID requestId) {
        AdminContext.currentOrThrow();
        modelSchemaValidator.validateModelId(modelId);
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
        bindAllFeatures(modelId);
        long catalogVersion = bumpProviderVersion(provider);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_MODEL_CREATED,
                "model_catalog",
                null,
                null,
                "{\"provider\":\"" + provider.id() + "\",\"model_id\":\"" + modelId + "\"}",
                "Manual catalog model create",
                requestIp,
                requestId);
        applicationEventPublisher.publishEvent(
                new CatalogChangedEvent(
                        provider,
                        List.of(modelId),
                        EnumSet.allOf(Feature.class),
                        clock.instant(),
                        catalogVersion));
    }

    @Transactional
    public void disableModel(
            String modelId,
            String reason,
            boolean confirmedPinned,
            int pinnedCountAcknowledged,
            String requestIp,
            UUID requestId) {
        AdminContext.currentOrThrow();
        long pinnedTenantCount = pinnedTenantCount(modelId);
        if (pinnedTenantCount > 0
                && (!confirmedPinned || pinnedCountAcknowledged != (int) pinnedTenantCount)) {
            throw new CatalogDisablePinsUnconfirmedException(pinnedTenantCount);
        }
        LlmProvider provider = providerForModel(modelId);
        int updatedRows =
                jdbcTemplate.update(
                        """
                        UPDATE model_catalog
                        SET deprecated_at = COALESCE(deprecated_at, NOW()),
                            updated_at = NOW()
                        WHERE model_id = ?
                        """,
                        modelId);
        if (updatedRows == 0) {
            throw new FeatureDefaultProviderService.CatalogModelNotFoundException(modelId);
        }
        long catalogVersion = bumpProviderVersion(provider);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_MODEL_DISABLED,
                "model_catalog",
                null,
                null,
                "{\"model_id\":\""
                        + modelId
                        + "\",\"pinned_tenant_count\":"
                        + pinnedTenantCount
                        + "}",
                reason,
                requestIp,
                requestId);
        applicationEventPublisher.publishEvent(
                new CatalogChangedEvent(
                        provider,
                        List.of(modelId),
                        EnumSet.allOf(Feature.class),
                        clock.instant(),
                        catalogVersion));
    }

    @Transactional
    public void setDefault(
            LlmProvider provider,
            Feature feature,
            String modelId,
            String reason,
            String requestIp,
            UUID requestId) {
        LlmProvider actualProvider = providerForModel(modelId);
        if (actualProvider != provider) {
            throw new FeatureDefaultProviderService.CatalogModelNotFoundException(modelId);
        }
        featureDefaultProviderService.set(feature, modelId, reason, requestIp, requestId);
    }

    void upsertFetchedModel(LlmProvider provider, CatalogModelRow row) {
        modelSchemaValidator.validateModelId(row.modelId());
        jdbcTemplate.update(
                """
                INSERT INTO model_catalog(model_id, provider, display_name, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (model_id) DO UPDATE
                SET display_name = EXCLUDED.display_name,
                    deprecated_at = NULL,
                    updated_at = NOW()
                """,
                row.modelId(),
                provider.id(),
                row.displayName());
        bindAllFeatures(row.modelId());
    }

    void softDeleteFetchedModel(String modelId) {
        jdbcTemplate.update(
                """
                UPDATE model_catalog
                SET deprecated_at = COALESCE(deprecated_at, NOW()),
                    updated_at = NOW()
                WHERE model_id = ?
                """,
                modelId);
    }

    long bumpProviderVersion(LlmProvider provider) {
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

    private void bindAllFeatures(String modelId) {
        for (Feature feature : Feature.values()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO feature_binding(model_id, feature)
                    VALUES (?, ?)
                    ON CONFLICT (model_id, feature) DO UPDATE SET enabled = TRUE
                    """,
                    modelId,
                    feature.id());
        }
    }

    private long pinnedTenantCount(String modelId) {
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

    private LlmProvider providerForModel(String modelId) {
        return jdbcTemplate
                .query(
                        "SELECT provider FROM model_catalog WHERE model_id = ?",
                        (resultSet, rowNumber) ->
                                LlmProvider.fromId(resultSet.getString("provider")),
                        modelId)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new FeatureDefaultProviderService.CatalogModelNotFoundException(
                                        modelId));
    }

    private ProviderModelRow mapProviderModelRow(ResultSet resultSet, int rowNumber)
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
        java.sql.Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record ProviderModelRow(Feature feature, CatalogModelRow catalogModelRow) {}

    public static class CatalogDisablePinsUnconfirmedException extends AdminBusinessException {

        private final long pinnedTenantCount;

        public CatalogDisablePinsUnconfirmedException(long pinnedTenantCount) {
            super("Pinned tenants require catalog disable confirmation");
            this.pinnedTenantCount = pinnedTenantCount;
        }

        public long pinnedTenantCount() {
            return pinnedTenantCount;
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.catalog_disable_pins_unconfirmed";
        }

        @Override
        public String logEvent() {
            return "admin_catalog_disable_pins_unconfirmed";
        }

        @Override
        public String detail() {
            return "Disabling this catalog model requires explicit confirmation because tenants pin it.";
        }

        @Override
        public Map<String, Object> params() {
            return Map.of("pinnedTenantCount", pinnedTenantCount);
        }
    }
}
