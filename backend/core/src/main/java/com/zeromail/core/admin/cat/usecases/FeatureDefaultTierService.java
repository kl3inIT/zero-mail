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
import com.zeromail.core.admin.cat.persistence.FeatureTierModelEntity;
import com.zeromail.core.admin.cat.persistence.FeatureTierModelRepository;
import com.zeromail.core.admin.cat.persistence.ModelCatalogEntity;
import com.zeromail.core.admin.cat.persistence.ModelCatalogRepository;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin use cases for the 3-tier routing matrix (A1 design). Each (feature, tier) anchors ONE
 * provider plus an ordered list of models — the router walks the model list inside a tier before
 * escalating to the next tier.
 */
@Service
public class FeatureDefaultTierService {

    private final FeatureDefaultProviderRepository featureDefaultProviderRepository;
    private final FeatureTierModelRepository featureTierModelRepository;
    private final ModelCatalogRepository modelCatalogRepository;
    private final AdminAuditWriter adminAuditWriter;
    private final Clock clock;

    public FeatureDefaultTierService(
            FeatureDefaultProviderRepository featureDefaultProviderRepository,
            FeatureTierModelRepository featureTierModelRepository,
            ModelCatalogRepository modelCatalogRepository,
            AdminAuditWriter adminAuditWriter,
            Clock clock) {
        this.featureDefaultProviderRepository =
                Objects.requireNonNull(
                        featureDefaultProviderRepository, "featureDefaultProviderRepository");
        this.featureTierModelRepository =
                Objects.requireNonNull(featureTierModelRepository, "featureTierModelRepository");
        this.modelCatalogRepository =
                Objects.requireNonNull(modelCatalogRepository, "modelCatalogRepository");
        this.adminAuditWriter = Objects.requireNonNull(adminAuditWriter, "adminAuditWriter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** All tier-anchor rows (one per (feature, tier)) ordered by feature then tier. */
    @Transactional(readOnly = true)
    public List<FeatureDefaultProviderEntity> listMatrix() {
        AdminContext.currentOrThrow();
        return featureDefaultProviderRepository.findAllOrderedByFeatureAndTier();
    }

    /** Ordered models for one tier. Empty when the tier is unassigned. */
    @Transactional(readOnly = true)
    public List<FeatureTierModelEntity> listTierModels(Feature feature, RoutingTier tier) {
        AdminContext.currentOrThrow();
        return featureTierModelRepository.findByFeatureAndTierOrderByPosition(feature, tier);
    }

    /** Every tier-model row for one feature, grouped by tier then position. */
    @Transactional(readOnly = true)
    public List<FeatureTierModelEntity> listAllTierModels(Feature feature) {
        AdminContext.currentOrThrow();
        return featureTierModelRepository.findByFeatureOrderByTierAndPosition(feature);
    }

    /** Every tier-model row across every feature, ordered by feature, then tier, then position. */
    @Transactional(readOnly = true)
    public List<FeatureTierModelEntity> listAllTierModelsAcrossFeatures() {
        AdminContext.currentOrThrow();
        return featureTierModelRepository.findAll().stream()
                .sorted(
                        (left, right) -> {
                            int byFeature = left.getFeature().compareTo(right.getFeature());
                            if (byFeature != 0) return byFeature;
                            int byTier = left.getTier().compareTo(right.getTier());
                            if (byTier != 0) return byTier;
                            return Short.compare(left.getPosition(), right.getPosition());
                        })
                .toList();
    }

    /**
     * Assigns a (provider, ordered models) pair to one tier slot. The {@code orderedModelIds} list
     * defines the fallback order inside the tier — position 1 first, then 2, etc. Replaces any
     * existing model rows for that tier atomically.
     */
    @Transactional
    public void assign(
            Feature feature,
            RoutingTier tier,
            LlmProvider provider,
            List<String> orderedModelIds,
            String requestIp,
            UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        Objects.requireNonNull(orderedModelIds, "orderedModelIds");
        if (orderedModelIds.isEmpty()) {
            throw new EmptyTierModelListException(feature, tier);
        }
        // Reject duplicate model ids — the UI sometimes batches duplicates from rapid clicks.
        Set<String> uniqueModelIds = new LinkedHashSet<>(orderedModelIds);
        if (uniqueModelIds.size() != orderedModelIds.size()) {
            throw new DuplicateTierModelException(feature, tier);
        }

        // Every model must exist, belong to the supplied provider, be undeprecated, and be
        // VERIFIED or STALE. Validating once here lets the router stay loose.
        for (String modelId : uniqueModelIds) {
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
            if (status != ModelVerificationStatus.VERIFIED
                    && status != ModelVerificationStatus.STALE) {
                throw new ModelNotEligibleException(modelId);
            }
        }

        Instant now = clock.instant();

        // Upsert the (feature, tier) anchor row.
        FeatureDefaultProviderEntity anchor =
                featureDefaultProviderRepository
                        .findBinding(feature, tier)
                        .orElseGet(
                                () ->
                                        new FeatureDefaultProviderEntity(
                                                feature, tier, provider, adminUser.id(), now));
        anchor.assignTo(provider, adminUser.id(), now);
        featureDefaultProviderRepository.save(anchor);

        // Atomically replace the ordered model list. CASCADE on the FK takes the rows down to
        // empty first, then we insert the new positions.
        featureTierModelRepository.deleteByFeatureAndTier(feature, tier);
        featureTierModelRepository.flush();
        short position = 1;
        for (String modelId : uniqueModelIds) {
            featureTierModelRepository.save(
                    new FeatureTierModelEntity(feature, tier, position, modelId, now));
            position++;
        }

        adminAuditWriter.append(
                AdminAuditAction.CATALOG_FEATURE_DEFAULT_SET,
                "feature_default_provider",
                null,
                (Map<String, ?>) null,
                Map.of(
                        "feature", feature.id(),
                        "tier", tier.id(),
                        "provider", provider.id(),
                        "model_ids", List.copyOf(uniqueModelIds)),
                null,
                requestIp,
                requestId);
    }

    /**
     * Drops the tier slot entirely — both the anchor row and the ordered model list. Idempotent on
     * a missing slot.
     */
    @Transactional
    public void clear(Feature feature, RoutingTier tier, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        featureTierModelRepository.deleteByFeatureAndTier(feature, tier);
        featureDefaultProviderRepository
                .findBinding(feature, tier)
                .ifPresent(featureDefaultProviderRepository::delete);
        adminAuditWriter.append(
                AdminAuditAction.CATALOG_FEATURE_DEFAULT_SET,
                "feature_default_provider",
                null,
                (Map<String, ?>) null,
                Map.of(
                        "feature", feature.id(),
                        "tier", tier.id(),
                        "action", "CLEAR"),
                "Cleared tier slot",
                requestIp,
                requestId);
    }

    /**
     * Best-effort cross-provider check. Warns (does not block) when two tiers of the same feature
     * share a provider — that's an anti-pattern (correlated outages) but the operator may have a
     * reason. Returns the set of features that fail the check.
     */
    @Transactional(readOnly = true)
    public Set<Feature> findFeaturesWithoutCrossProviderTiers() {
        AdminContext.currentOrThrow();
        Set<Feature> warned = new HashSet<>();
        List<FeatureDefaultProviderEntity> matrix =
                featureDefaultProviderRepository.findAllOrderedByFeatureAndTier();
        Map<Feature, Set<LlmProvider>> providersByFeature = new java.util.HashMap<>();
        Map<Feature, Integer> tierCountByFeature = new java.util.HashMap<>();
        for (FeatureDefaultProviderEntity row : matrix) {
            providersByFeature
                    .computeIfAbsent(row.getFeature(), feature -> new HashSet<>())
                    .add(row.getProvider());
            tierCountByFeature.merge(row.getFeature(), 1, Integer::sum);
        }
        for (Map.Entry<Feature, Set<LlmProvider>> entry : providersByFeature.entrySet()) {
            int tierCount = tierCountByFeature.getOrDefault(entry.getKey(), 0);
            if (tierCount > 1 && entry.getValue().size() < tierCount) {
                warned.add(entry.getKey());
            }
        }
        return warned;
    }

    public static final class EmptyTierModelListException extends AdminBusinessException {

        public EmptyTierModelListException(Feature feature, RoutingTier tier) {
            super("Tier " + feature.id() + "/" + tier.id() + " must contain at least one model");
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.feature_default_tier_empty";
        }

        @Override
        public String logEvent() {
            return "admin_feature_default_tier_empty";
        }

        @Override
        public String detail() {
            return "A tier slot must list at least one model.";
        }
    }

    public static final class DuplicateTierModelException extends AdminBusinessException {

        public DuplicateTierModelException(Feature feature, RoutingTier tier) {
            super(
                    "Tier "
                            + feature.id()
                            + "/"
                            + tier.id()
                            + " contains duplicate model ids in its ordered list");
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "error.admin.feature_default_tier_duplicate_model";
        }

        @Override
        public String logEvent() {
            return "admin_feature_default_tier_duplicate_model";
        }

        @Override
        public String detail() {
            return "Each tier's model list must contain unique model ids.";
        }
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
