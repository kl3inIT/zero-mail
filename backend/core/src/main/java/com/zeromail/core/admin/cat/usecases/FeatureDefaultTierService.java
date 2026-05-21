package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.cat.persistence.FeatureDefaultProviderEntity;
import com.zeromail.core.admin.cat.persistence.FeatureDefaultProviderRepository;
import com.zeromail.core.admin.cat.persistence.ModelCatalogEntity;
import com.zeromail.core.admin.cat.persistence.ModelCatalogRepository;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin use cases for the 3-tier routing matrix. Each feature has up to three configured rows
 * (PRIMARY, FALLBACK, LAST_RESORT) that together form its failover chain. This service is the
 * single write path for those rows.
 */
@Service
public class FeatureDefaultTierService {

    private final FeatureDefaultProviderRepository featureDefaultProviderRepository;
    private final ModelCatalogRepository modelCatalogRepository;
    private final AdminAuditWriter adminAuditWriter;
    private final Clock clock;

    public FeatureDefaultTierService(
            FeatureDefaultProviderRepository featureDefaultProviderRepository,
            ModelCatalogRepository modelCatalogRepository,
            AdminAuditWriter adminAuditWriter,
            Clock clock) {
        this.featureDefaultProviderRepository =
                Objects.requireNonNull(
                        featureDefaultProviderRepository, "featureDefaultProviderRepository");
        this.modelCatalogRepository =
                Objects.requireNonNull(modelCatalogRepository, "modelCatalogRepository");
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public List<FeatureDefaultProviderEntity> listMatrix() {
        AdminContext.currentOrThrow();
        return featureDefaultProviderRepository.findAllOrderedByFeatureAndTier();
    }

    @Transactional
    public void assign(
            Feature feature,
            RoutingTier tier,
            LlmProvider provider,
            String modelId,
            String requestIp,
            UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();

        // The tier picker on the FE only surfaces (provider, model) pairs that
        // are ACTIVE and VERIFIED. Re-validate server-side: the catalog row
        // must exist, be undeprecated, and belong to the same provider.
        ModelCatalogEntity modelRow =
                modelCatalogRepository
                        .findById(modelId)
                        .orElseThrow(() -> new ModelNotEligibleException(modelId));
        if (modelRow.getProvider() != provider) {
            throw new ModelProviderMismatchException(modelId, provider);
        }
        if (modelRow.getDeprecatedAt() != null) {
            throw new ModelNotEligibleException(modelId);
        }
        ModelVerificationStatus status = modelRow.getVerificationStatus();
        if (status != ModelVerificationStatus.VERIFIED && status != ModelVerificationStatus.STALE) {
            throw new ModelNotEligibleException(modelId);
        }

        FeatureDefaultProviderEntity row =
                featureDefaultProviderRepository
                        .findBinding(feature, tier)
                        .orElseGet(
                                () ->
                                        new FeatureDefaultProviderEntity(
                                                feature,
                                                tier,
                                                provider,
                                                modelId,
                                                adminUser.id(),
                                                clock.instant()));
        row.assignTo(provider, modelId, adminUser.id(), clock.instant());
        featureDefaultProviderRepository.save(row);

        adminAuditWriter.append(
                AdminAuditAction.CATALOG_FEATURE_DEFAULT_SET,
                "feature_default_provider",
                null,
                (Map<String, ?>) null,
                Map.of(
                        "feature", feature.id(),
                        "tier", tier.id(),
                        "provider", provider.id(),
                        "model_id", modelId),
                null,
                requestIp,
                requestId);
    }

    public static final class ModelNotEligibleException extends AdminBusinessException {

        public ModelNotEligibleException(String modelId) {
            super("Model " + modelId + " is not eligible for the routing matrix");
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.CONFLICT;
        }

        @Override
        public String errorCode() {
            return "error.admin.feature_default_model_not_eligible";
        }

        @Override
        public String logEvent() {
            return "admin_feature_default_model_not_eligible";
        }

        @Override
        public String detail() {
            return "Only ACTIVE providers with VERIFIED or STALE models can be assigned to a tier.";
        }
    }

    public static final class ModelProviderMismatchException extends AdminBusinessException {

        public ModelProviderMismatchException(String modelId, LlmProvider provider) {
            super("Model " + modelId + " does not belong to provider " + provider.id());
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.CONFLICT;
        }

        @Override
        public String errorCode() {
            return "error.admin.feature_default_provider_mismatch";
        }

        @Override
        public String logEvent() {
            return "admin_feature_default_provider_mismatch";
        }

        @Override
        public String detail() {
            return "The model does not belong to the supplied provider.";
        }
    }
}
