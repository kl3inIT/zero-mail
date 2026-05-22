package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.cat.persistence.lowlevel.ModelCatalogWriteRepository;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy single-model API kept so the {@code PUT /api/admin/catalog/{provider}/{feature}/default}
 * endpoint still works. It now delegates to {@link FeatureDefaultTierService#assign} with the
 * PRIMARY tier and a single-element model list — the schema no longer permits a single model column
 * on {@code feature_default_provider}.
 */
@Service
public class FeatureDefaultProviderService {

    private final ModelCatalogWriteRepository modelCatalogWriteRepository;
    private final FeatureDefaultTierService featureDefaultTierService;

    public FeatureDefaultProviderService(
            ModelCatalogWriteRepository modelCatalogWriteRepository,
            FeatureDefaultTierService featureDefaultTierService) {
        this.modelCatalogWriteRepository =
                Objects.requireNonNull(
                        modelCatalogWriteRepository,
                        "modelCatalogWriteRepository must not be null");
        this.featureDefaultTierService =
                Objects.requireNonNull(featureDefaultTierService, "featureDefaultTierService");
    }

    @Transactional
    public void set(
            Feature feature, String modelId, String reason, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        LlmProvider provider =
                modelCatalogWriteRepository
                        .findProviderForModel(modelId)
                        .orElseThrow(() -> new CatalogModelNotFoundException(modelId));
        featureDefaultTierService.assign(
                feature, RoutingTier.PRIMARY, provider, List.of(modelId), requestIp, requestId);
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
