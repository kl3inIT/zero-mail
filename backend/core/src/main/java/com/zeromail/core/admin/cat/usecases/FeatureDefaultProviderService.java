package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.event.CatalogChangedEvent;
import com.zeromail.core.admin.cat.persistence.lowlevel.ModelCatalogWriteRepository;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureDefaultProviderService {

    private final ModelCatalogWriteRepository modelCatalogWriteRepository;
    private final AdminAuditWriter adminAuditWriter;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public FeatureDefaultProviderService(
            ModelCatalogWriteRepository modelCatalogWriteRepository,
            AdminAuditWriter adminAuditWriter,
            ApplicationEventPublisher applicationEventPublisher,
            Clock clock) {
        this.modelCatalogWriteRepository =
                Objects.requireNonNull(
                        modelCatalogWriteRepository,
                        "modelCatalogWriteRepository must not be null");
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
                modelCatalogWriteRepository
                        .findActiveProviderForModel(modelId)
                        .orElseThrow(() -> new CatalogModelNotFoundException(modelId));
        modelCatalogWriteRepository.upsertFeatureDefault(
                feature, modelId, provider, adminUser.id());
        long newCatalogVersion =
                modelCatalogWriteRepository.bumpProviderCatalogVersionById(provider);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_FEATURE_DEFAULT_SET,
                "feature_default_provider",
                null,
                null,
                Map.of("feature", feature.id(), "provider", provider, "model_id", modelId),
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
                modelCatalogWriteRepository
                        .findFirstRecommendedModelId(provider, feature)
                        .orElseThrow(() -> new CatalogModelNotFoundException(provider.id()));
        set(feature, modelId, reason, requestIp, requestId);
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
