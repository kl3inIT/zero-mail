package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.cat.persistence.FeatureDefaultProviderEntity;
import com.zeromail.core.admin.cat.persistence.FeatureDefaultProviderRepository;
import com.zeromail.core.admin.cat.persistence.FeatureTierModelEntity;
import com.zeromail.core.admin.cat.persistence.FeatureTierModelRepository;
import com.zeromail.core.admin.cat.persistence.ModelCatalogEntity;
import com.zeromail.core.admin.cat.persistence.ModelCatalogRepository;
import com.zeromail.core.admin.cat.projection.ResolvedRoute;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.MasterKeyStatus;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyEntity;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the platform-side LLM route for a feature by walking the 3-tier failover chain. Each
 * tier names ONE provider plus an ordered list of models (positions 1..N). The router walks two
 * nested loops:
 *
 * <ol>
 *   <li>Outer: tiers in PRIMARY → FALLBACK → LAST_RESORT order.
 *   <li>Inner: models in position 1 → 2 → ... → N order, all served by the same provider.
 * </ol>
 *
 * Within a (tier, model) cell the router picks the lowest-priority ACTIVE key of the provider —
 * key-level fallback within a single attempt is deliberately omitted; failover happens by advancing
 * to the next model or tier slot instead. Only VERIFIED and STALE models contribute to the chain
 * (FAILED / UNTESTED are skipped silently).
 *
 * <p>The router does not call any LLM — it only produces an ordered list of {@link ResolvedRoute}
 * steps. The chat adapter walks the list and stops on the first successful response.
 */
@Service
public class LlmRouter {

    private final FeatureDefaultProviderRepository featureDefaultProviderRepository;
    private final FeatureTierModelRepository featureTierModelRepository;
    private final LlmProviderMasterKeyRepository llmProviderMasterKeyRepository;
    private final ModelCatalogRepository modelCatalogRepository;

    public LlmRouter(
            FeatureDefaultProviderRepository featureDefaultProviderRepository,
            FeatureTierModelRepository featureTierModelRepository,
            LlmProviderMasterKeyRepository llmProviderMasterKeyRepository,
            ModelCatalogRepository modelCatalogRepository) {
        this.featureDefaultProviderRepository =
                Objects.requireNonNull(
                        featureDefaultProviderRepository, "featureDefaultProviderRepository");
        this.featureTierModelRepository =
                Objects.requireNonNull(featureTierModelRepository, "featureTierModelRepository");
        this.llmProviderMasterKeyRepository =
                Objects.requireNonNull(
                        llmProviderMasterKeyRepository, "llmProviderMasterKeyRepository");
        this.modelCatalogRepository =
                Objects.requireNonNull(modelCatalogRepository, "modelCatalogRepository");
    }

    /**
     * Build the full failover plan for a feature. Returns one step per usable (tier, model) cell,
     * in walk order. May be empty when no tier has a VERIFIED model with an ACTIVE key.
     */
    @Transactional(readOnly = true)
    public List<ResolvedRoute> resolve(Feature feature) {
        Objects.requireNonNull(feature, "feature");
        List<FeatureDefaultProviderEntity> tierBindings =
                featureDefaultProviderRepository.findByFeatureOrderByTier(feature);

        List<ResolvedRoute> walk = new ArrayList<>();
        for (FeatureDefaultProviderEntity tierBinding : tierBindings) {
            appendTierSteps(walk, feature, tierBinding);
        }
        return List.copyOf(walk);
    }

    /**
     * Convenience helper for callers that just want the first usable step. Equivalent to {@code
     * resolve(feature).stream().findFirst()}.
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedRoute> resolvePrimary(Feature feature) {
        List<ResolvedRoute> walk = resolve(feature);
        return walk.isEmpty() ? Optional.empty() : Optional.of(walk.get(0));
    }

    /**
     * Walk only the requested tier. Used by ops tooling that wants to test a single tier slot
     * without triggering automatic fallback into the next tier.
     */
    @Transactional(readOnly = true)
    public List<ResolvedRoute> resolveTier(Feature feature, RoutingTier tier) {
        return featureDefaultProviderRepository
                .findBinding(feature, tier)
                .map(
                        binding -> {
                            List<ResolvedRoute> walk = new ArrayList<>();
                            appendTierSteps(walk, feature, binding);
                            return List.copyOf(walk);
                        })
                .orElseGet(List::of);
    }

    private void appendTierSteps(
            List<ResolvedRoute> walk, Feature feature, FeatureDefaultProviderEntity tierBinding) {
        LlmProvider provider = tierBinding.getProvider();
        Optional<LlmProviderMasterKeyEntity> activeKey = pickHighestPriorityActiveKey(provider);
        if (activeKey.isEmpty()) {
            return;
        }
        LlmProviderMasterKeyEntity key = activeKey.get();

        List<FeatureTierModelEntity> models =
                featureTierModelRepository.findByFeatureAndTierOrderByPosition(
                        feature, tierBinding.getTier());
        for (FeatureTierModelEntity modelSlot : models) {
            if (!isModelRoutable(provider, modelSlot.getModelId())) {
                continue;
            }
            walk.add(
                    new ResolvedRoute(
                            feature,
                            tierBinding.getTier(),
                            provider,
                            modelSlot.getModelId(),
                            key.getKeyId(),
                            key.getPriority()));
        }
    }

    private Optional<LlmProviderMasterKeyEntity> pickHighestPriorityActiveKey(
            LlmProvider provider) {
        List<LlmProviderMasterKeyEntity> activeKeys =
                llmProviderMasterKeyRepository.findActiveByProviderOrderByPriority(
                        provider, MasterKeyStatus.ACTIVE);
        return activeKeys.isEmpty() ? Optional.empty() : Optional.of(activeKeys.get(0));
    }

    private boolean isModelRoutable(LlmProvider provider, String modelId) {
        Optional<ModelCatalogEntity> model = modelCatalogRepository.findById(modelId);
        if (model.isEmpty()) return false;
        ModelCatalogEntity modelRow = model.get();
        if (modelRow.getProvider() != provider) return false;
        if (modelRow.getDeprecatedAt() != null) return false;
        ModelVerificationStatus status = modelRow.getVerificationStatus();
        // STALE is still routable — operators have been notified to re-test
        // but the model proved itself recently. FAILED / UNTESTED are not.
        return status == ModelVerificationStatus.VERIFIED
                || status == ModelVerificationStatus.STALE;
    }
}
