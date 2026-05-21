package com.zeromail.core.admin.cat.usecases;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.cat.persistence.FeatureDefaultProviderEntity;
import com.zeromail.core.admin.cat.persistence.FeatureDefaultProviderRepository;
import com.zeromail.core.admin.cat.persistence.ModelCatalogEntity;
import com.zeromail.core.admin.cat.persistence.ModelCatalogRepository;
import com.zeromail.core.admin.cat.projection.ResolvedRoute;
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
 * tier (PRIMARY → FALLBACK → LAST_RESORT) names a (provider, model) pair; inside a tier the router
 * fans out across the provider's ACTIVE keys in priority order so a single dead key does not
 * collapse the whole tier.
 *
 * <p>Only VERIFIED models contribute to the chain. STALE / FAILED / UNTESTED rows are skipped
 * silently so an operator can rotate without taking the route offline.
 *
 * <p>The router does not call any LLM — it only produces an ordered list of {@link ResolvedRoute}
 * steps. The chat adapter walks the list and stops on the first successful response.
 */
@Service
public class LlmRouter {

    private final FeatureDefaultProviderRepository featureDefaultProviderRepository;
    private final LlmProviderMasterKeyRepository llmProviderMasterKeyRepository;
    private final ModelCatalogRepository modelCatalogRepository;

    public LlmRouter(
            FeatureDefaultProviderRepository featureDefaultProviderRepository,
            LlmProviderMasterKeyRepository llmProviderMasterKeyRepository,
            ModelCatalogRepository modelCatalogRepository) {
        this.featureDefaultProviderRepository =
                Objects.requireNonNull(
                        featureDefaultProviderRepository, "featureDefaultProviderRepository");
        this.llmProviderMasterKeyRepository =
                Objects.requireNonNull(
                        llmProviderMasterKeyRepository, "llmProviderMasterKeyRepository");
        this.modelCatalogRepository =
                Objects.requireNonNull(modelCatalogRepository, "modelCatalogRepository");
    }

    /**
     * Build the full failover plan for a feature. Returns one step per usable (tier, provider, key)
     * combination, in walk order. May be empty when no tier has a VERIFIED model with an ACTIVE
     * key.
     */
    @Transactional(readOnly = true)
    public List<ResolvedRoute> resolve(Feature feature) {
        Objects.requireNonNull(feature, "feature");
        List<FeatureDefaultProviderEntity> tierBindings =
                featureDefaultProviderRepository.findByFeatureOrderByTier(feature);

        List<ResolvedRoute> walk = new ArrayList<>();
        for (FeatureDefaultProviderEntity tierBinding : tierBindings) {
            if (!isModelRoutable(tierBinding.getProvider(), tierBinding.getModelId())) {
                continue;
            }
            List<LlmProviderMasterKeyEntity> activeKeys =
                    llmProviderMasterKeyRepository.findActiveByProviderOrderByPriority(
                            tierBinding.getProvider(), MasterKeyStatus.ACTIVE);
            for (LlmProviderMasterKeyEntity activeKey : activeKeys) {
                walk.add(
                        new ResolvedRoute(
                                feature,
                                tierBinding.getTier(),
                                tierBinding.getProvider(),
                                tierBinding.getModelId(),
                                activeKey.getKeyId(),
                                activeKey.getPriority()));
            }
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
     * without triggering automatic fallback.
     */
    @Transactional(readOnly = true)
    public List<ResolvedRoute> resolveTier(Feature feature, RoutingTier tier) {
        return featureDefaultProviderRepository
                .findBinding(feature, tier)
                .filter(binding -> isModelRoutable(binding.getProvider(), binding.getModelId()))
                .map(
                        binding -> {
                            List<LlmProviderMasterKeyEntity> activeKeys =
                                    llmProviderMasterKeyRepository
                                            .findActiveByProviderOrderByPriority(
                                                    binding.getProvider(), MasterKeyStatus.ACTIVE);
                            List<ResolvedRoute> walk = new ArrayList<>(activeKeys.size());
                            for (LlmProviderMasterKeyEntity activeKey : activeKeys) {
                                walk.add(
                                        new ResolvedRoute(
                                                feature,
                                                tier,
                                                binding.getProvider(),
                                                binding.getModelId(),
                                                activeKey.getKeyId(),
                                                activeKey.getPriority()));
                            }
                            return List.copyOf(walk);
                        })
                .orElseGet(List::of);
    }

    private boolean isModelRoutable(
            com.zeromail.core.admin.mkey.domain.LlmProvider provider, String modelId) {
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
