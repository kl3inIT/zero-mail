package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.event.CatalogChangedEvent;
import com.zeromail.core.admin.cat.persistence.lowlevel.ModelCatalogWriteRepository;
import com.zeromail.core.admin.cat.persistence.lowlevel.ModelCatalogWriteRepository.ProviderModelRow;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import com.zeromail.core.admin.cat.projection.PerFeatureCatalog;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogAdminService {

    private final ModelCatalogWriteRepository modelCatalogWriteRepository;
    private final ModelSchemaValidator modelSchemaValidator;
    private final AdminAuditWriter adminAuditWriter;
    private final FeatureDefaultProviderService featureDefaultProviderService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public CatalogAdminService(
            ModelCatalogWriteRepository modelCatalogWriteRepository,
            ModelSchemaValidator modelSchemaValidator,
            AdminAuditWriter adminAuditWriter,
            FeatureDefaultProviderService featureDefaultProviderService,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.modelCatalogWriteRepository =
                Objects.requireNonNull(
                        modelCatalogWriteRepository,
                        "modelCatalogWriteRepository must not be null");
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
        List<ProviderModelRow> rows = modelCatalogWriteRepository.findProviderModelRows(provider);
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
        modelCatalogWriteRepository.insertManualModel(
                provider, modelId, displayName, costPer1kInput, costPer1kOutput, recommended);
        bindAllFeatures(modelId);
        long catalogVersion = modelCatalogWriteRepository.bumpProviderCatalogVersion(provider);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_MODEL_CREATED,
                "model_catalog",
                null,
                null,
                Map.of("provider", provider.id(), "model_id", modelId),
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
        long pinnedTenantCount = modelCatalogWriteRepository.countPinnedTenants(modelId);
        if (pinnedTenantCount > 0
                && (!confirmedPinned || pinnedCountAcknowledged != (int) pinnedTenantCount)) {
            throw new CatalogDisablePinsUnconfirmedException(pinnedTenantCount);
        }
        LlmProvider provider =
                modelCatalogWriteRepository
                        .findProviderForModel(modelId)
                        .orElseThrow(
                                () ->
                                        new FeatureDefaultProviderService
                                                .CatalogModelNotFoundException(modelId));
        int updatedRows = modelCatalogWriteRepository.softDeleteModel(modelId);
        if (updatedRows == 0) {
            throw new FeatureDefaultProviderService.CatalogModelNotFoundException(modelId);
        }
        long catalogVersion = modelCatalogWriteRepository.bumpProviderCatalogVersion(provider);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_MODEL_DISABLED,
                "model_catalog",
                null,
                null,
                Map.of("model_id", modelId, "pinned_tenant_count", pinnedTenantCount),
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
        LlmProvider actualProvider =
                modelCatalogWriteRepository
                        .findProviderForModel(modelId)
                        .orElseThrow(
                                () ->
                                        new FeatureDefaultProviderService
                                                .CatalogModelNotFoundException(modelId));
        if (!actualProvider.equals(provider)) {
            throw new FeatureDefaultProviderService.CatalogModelNotFoundException(modelId);
        }
        featureDefaultProviderService.set(feature, modelId, reason, requestIp, requestId);
    }

    void upsertFetchedModel(LlmProvider provider, CatalogModelRow row) {
        modelSchemaValidator.validateModelId(row.modelId());
        modelCatalogWriteRepository.upsertFetchedModel(provider, row.modelId(), row.displayName());
        bindAllFeatures(row.modelId());
    }

    void softDeleteFetchedModel(String modelId) {
        modelCatalogWriteRepository.softDeleteModel(modelId);
    }

    long bumpProviderVersion(LlmProvider provider) {
        return modelCatalogWriteRepository.bumpProviderCatalogVersion(provider);
    }

    private void bindAllFeatures(String modelId) {
        for (Feature feature : Feature.values()) {
            modelCatalogWriteRepository.upsertFeatureBinding(modelId, feature);
        }
    }

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
