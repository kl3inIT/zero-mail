package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.event.CatalogChangedEvent;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureDefaultProviderService {

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditWriter adminAuditWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public FeatureDefaultProviderService(
            JdbcTemplate jdbcTemplate,
            AdminAuditWriter adminAuditWriter,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
        this.applicationEventPublisher =
                Objects.requireNonNull(applicationEventPublisher, "applicationEventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public void set(
            Feature feature, String modelId, String reason, String requestIp, UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        String provider =
                jdbcTemplate
                        .query(
                                """
                                SELECT provider
                                FROM model_catalog
                                WHERE model_id = ?
                                  AND deprecated_at IS NULL
                                """,
                                (resultSet, rowNumber) -> resultSet.getString("provider"),
                                modelId)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new CatalogModelNotFoundException(modelId));
        upsert(feature, modelId, provider, adminUser.id());
        long newCatalogVersion = bumpProviderVersion(provider);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_FEATURE_DEFAULT_SET,
                "feature_default_provider",
                null,
                null,
                "{\"feature\":\""
                        + feature.id()
                        + "\",\"provider\":\""
                        + provider
                        + "\",\"model_id\":\""
                        + modelId
                        + "\"}",
                reason,
                requestIp,
                requestId);
        applicationEventPublisher.publishEvent(
                new CatalogChangedEvent(
                        LlmProvider.fromId(provider),
                        List.of(modelId),
                        Set.of(feature),
                        clock.instant(),
                        newCatalogVersion));
    }

    @Transactional
    public void setProviderDefault(
            Feature feature,
            LlmProvider provider,
            String reason,
            String requestIp,
            UUID requestId) {
        String modelId =
                jdbcTemplate
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
                                (resultSet, rowNumber) -> resultSet.getString("model_id"),
                                provider.id(),
                                feature.id())
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new CatalogModelNotFoundException(provider.id()));
        set(feature, modelId, reason, requestIp, requestId);
    }

    private void upsert(Feature feature, String modelId, String provider, UUID adminId) {
        jdbcTemplate.update(
                """
                INSERT INTO feature_default_provider(feature, provider, model_id, updated_by_admin, updated_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (feature) DO UPDATE
                SET provider = EXCLUDED.provider,
                    model_id = EXCLUDED.model_id,
                    updated_by_admin = EXCLUDED.updated_by_admin,
                    updated_at = NOW()
                """,
                feature.id(),
                provider,
                modelId,
                adminId);
    }

    private long bumpProviderVersion(String provider) {
        Long catalogVersion =
                jdbcTemplate.queryForObject(
                        """
                        UPDATE provider_catalog
                        SET catalog_version = catalog_version + 1
                        WHERE provider = ?
                        RETURNING catalog_version
                        """,
                        Long.class,
                        provider);
        return catalogVersion == null ? 1L : catalogVersion;
    }

    public static class CatalogModelNotFoundException extends AdminBusinessException {

        public CatalogModelNotFoundException(String modelId) {
            super("Catalog model not found: " + modelId);
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.NOT_FOUND;
        }

        @Override
        public String errorCode() {
            return "error.admin.catalog_model_not_found";
        }

        @Override
        public String logEvent() {
            return "admin_catalog_model_not_found";
        }

        @Override
        public String detail() {
            return "The requested catalog model could not be located.";
        }
    }
}
