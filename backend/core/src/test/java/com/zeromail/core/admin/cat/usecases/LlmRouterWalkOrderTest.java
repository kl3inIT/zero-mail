package com.zeromail.core.admin.cat.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.zeromail.core.llm.routing.LlmRuntimeTask;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * Invariant: {@link LlmRouter#resolve(Feature)} walks tiers in PRIMARY → FALLBACK → LAST_RESORT
 * order and inner models in position 1..N order, producing exactly one {@link ResolvedRoute} per
 * eligible (tier, position) cell. Also pins the Task-1 memoization fix: when every tier shares the
 * same provider, the active-key lookup must run at most once per distinct provider per resolve.
 *
 * <p>Test slice: plain JUnit + Mockito stubs on the four repository ports. Per TESTING.md §5 we
 * prefer Testcontainers over in-memory mocks for repository slices that exercise DB-side
 * invariants, but this test pins router walk order — pure use-case logic with no DB state to
 * exercise. Mockito stubs of the repository interfaces (call-recorded fakes) keep the test
 * deterministic and fast; the project's repository invariants are pinned by sibling IT suites such
 * as {@code ProviderCatalogLookupRepositoryFindAllFeatureDefaultPairsIT}.
 */
class LlmRouterWalkOrderTest {

    private static final UUID KEY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-22T10:00:00Z");

    @Test
    void resolve_runtime_task_maps_to_catalog_feature() {
        FeatureDefaultProviderRepository featureDefaultProviderRepository =
                mock(FeatureDefaultProviderRepository.class);
        FeatureTierModelRepository featureTierModelRepository =
                mock(FeatureTierModelRepository.class);
        LlmProviderMasterKeyRepository llmProviderMasterKeyRepository =
                mock(LlmProviderMasterKeyRepository.class);
        ModelCatalogRepository modelCatalogRepository = mock(ModelCatalogRepository.class);

        when(featureDefaultProviderRepository.findByFeatureOrderByTier(any()))
                .thenReturn(List.of());
        when(featureTierModelRepository.findByFeatureOrderByTierAndPosition(any()))
                .thenReturn(List.of());

        LlmRouter router =
                new LlmRouter(
                        featureDefaultProviderRepository,
                        featureTierModelRepository,
                        llmProviderMasterKeyRepository,
                        modelCatalogRepository);

        router.resolve(LlmRuntimeTask.CHAT_ASSISTANT);
        router.resolve(LlmRuntimeTask.TRIAGE_ACTION);
        router.resolve(LlmRuntimeTask.DRAFT_GENERATION);
        router.resolve(LlmRuntimeTask.RULE_AUTHORING);
        router.resolve(LlmRuntimeTask.RULE_PREVIEW_SEMANTIC);
        router.resolve(LlmRuntimeTask.TRIAGE_SEMANTIC);
        router.resolve(LlmRuntimeTask.DRIFT_CHECK);

        verify(featureDefaultProviderRepository).findByFeatureOrderByTier(Feature.CHAT);
        verify(featureDefaultProviderRepository).findByFeatureOrderByTier(Feature.TRIAGE);
        verify(featureDefaultProviderRepository).findByFeatureOrderByTier(Feature.DRAFT);
        verify(featureDefaultProviderRepository).findByFeatureOrderByTier(Feature.RULE_AUTHORING);
        verify(featureDefaultProviderRepository)
                .findByFeatureOrderByTier(Feature.RULE_PREVIEW_SEMANTIC);
        verify(featureDefaultProviderRepository).findByFeatureOrderByTier(Feature.TRIAGE_SEMANTIC);
        verify(featureDefaultProviderRepository).findByFeatureOrderByTier(Feature.DRIFT_CHECK);
    }

    @Test
    @DisplayName(
            "resolve(TRIAGE) walks tiers PRIMARY→FALLBACK→LAST_RESORT and models 1..N in order")
    void resolve_walks_tiers_outer_then_positions_inner_in_order() {
        FeatureDefaultProviderRepository featureDefaultProviderRepository =
                mock(FeatureDefaultProviderRepository.class);
        FeatureTierModelRepository featureTierModelRepository =
                mock(FeatureTierModelRepository.class);
        LlmProviderMasterKeyRepository llmProviderMasterKeyRepository =
                mock(LlmProviderMasterKeyRepository.class);
        ModelCatalogRepository modelCatalogRepository = mock(ModelCatalogRepository.class);

        // Tier bindings: every tier of TRIAGE points to OpenRouter.
        when(featureDefaultProviderRepository.findByFeatureOrderByTier(Feature.TRIAGE))
                .thenReturn(
                        List.of(
                                tierBinding(
                                        Feature.TRIAGE,
                                        RoutingTier.PRIMARY,
                                        LlmProvider.OPENROUTER),
                                tierBinding(
                                        Feature.TRIAGE,
                                        RoutingTier.FALLBACK,
                                        LlmProvider.OPENROUTER),
                                tierBinding(
                                        Feature.TRIAGE,
                                        RoutingTier.LAST_RESORT,
                                        LlmProvider.OPENROUTER)));

        // Tier slots: PRIMARY=[modelA, modelB], FALLBACK=[modelC], LAST_RESORT=[modelD, modelE].
        when(featureTierModelRepository.findByFeatureOrderByTierAndPosition(Feature.TRIAGE))
                .thenReturn(
                        List.of(
                                tierSlot(Feature.TRIAGE, RoutingTier.PRIMARY, (short) 1, "modelA"),
                                tierSlot(Feature.TRIAGE, RoutingTier.PRIMARY, (short) 2, "modelB"),
                                tierSlot(Feature.TRIAGE, RoutingTier.FALLBACK, (short) 1, "modelC"),
                                tierSlot(
                                        Feature.TRIAGE,
                                        RoutingTier.LAST_RESORT,
                                        (short) 1,
                                        "modelD"),
                                tierSlot(
                                        Feature.TRIAGE,
                                        RoutingTier.LAST_RESORT,
                                        (short) 2,
                                        "modelE")));

        // Catalog: all five models belong to OpenRouter, all VERIFIED, none deprecated.
        when(modelCatalogRepository.findAllById(ArgumentMatchers.<Iterable<String>>any()))
                .thenReturn(
                        List.of(
                                verifiedModel("modelA", LlmProvider.OPENROUTER),
                                verifiedModel("modelB", LlmProvider.OPENROUTER),
                                verifiedModel("modelC", LlmProvider.OPENROUTER),
                                verifiedModel("modelD", LlmProvider.OPENROUTER),
                                verifiedModel("modelE", LlmProvider.OPENROUTER)));

        // One ACTIVE key for OpenRouter at priority 1.
        LlmProviderMasterKeyEntity activeKey =
                new LlmProviderMasterKeyEntity(
                        LlmProvider.OPENROUTER,
                        KEY_ID,
                        1,
                        MasterKeyStatus.ACTIVE,
                        "primary",
                        null,
                        new byte[] {0x01},
                        (short) 1,
                        1L,
                        ACTOR_ID,
                        FIXED_INSTANT,
                        FIXED_INSTANT,
                        null,
                        "sk-...rout");
        when(llmProviderMasterKeyRepository.findActiveByProviderOrderByPriority(
                        eq(LlmProvider.OPENROUTER), eq(MasterKeyStatus.ACTIVE)))
                .thenReturn(List.of(activeKey));

        LlmRouter router =
                new LlmRouter(
                        featureDefaultProviderRepository,
                        featureTierModelRepository,
                        llmProviderMasterKeyRepository,
                        modelCatalogRepository);

        List<ResolvedRoute> walk = router.resolve(Feature.TRIAGE);

        // 1. Walk order: outer tier weight asc, inner position asc.
        assertThat(walk).hasSize(5);
        assertThat(walk)
                .extracting(ResolvedRoute::tier, ResolvedRoute::modelId)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(RoutingTier.PRIMARY, "modelA"),
                        org.assertj.core.api.Assertions.tuple(RoutingTier.PRIMARY, "modelB"),
                        org.assertj.core.api.Assertions.tuple(RoutingTier.FALLBACK, "modelC"),
                        org.assertj.core.api.Assertions.tuple(RoutingTier.LAST_RESORT, "modelD"),
                        org.assertj.core.api.Assertions.tuple(RoutingTier.LAST_RESORT, "modelE"));

        // 2. Every route resolves to the same OpenRouter key (provider repeats across tiers).
        assertThat(walk).allMatch(route -> route.provider() == LlmProvider.OPENROUTER);
        assertThat(walk).allMatch(route -> route.keyId().equals(KEY_ID));

        // 3. Memoization invariant — the highest-priority-active-key lookup must fire ONCE per
        // distinct provider per resolve, not once per tier. Three tiers, one provider -> 1 call.
        verify(llmProviderMasterKeyRepository)
                .findActiveByProviderOrderByPriority(
                        eq(LlmProvider.OPENROUTER), eq(MasterKeyStatus.ACTIVE));

        // 4. Batched catalog lookup invariant — model eligibility is checked via a single
        // findAllById call, not per-model findById. (findById should never be called.)
        verify(modelCatalogRepository).findAllById(any());
        verify(modelCatalogRepository, never()).findById(any());
    }

    private static FeatureDefaultProviderEntity tierBinding(
            Feature feature, RoutingTier tier, LlmProvider provider) {
        return new FeatureDefaultProviderEntity(feature, tier, provider, ACTOR_ID, FIXED_INSTANT);
    }

    private static FeatureTierModelEntity tierSlot(
            Feature feature, RoutingTier tier, short position, String modelId) {
        return new FeatureTierModelEntity(feature, tier, position, modelId, FIXED_INSTANT);
    }

    private static ModelCatalogEntity verifiedModel(String modelId, LlmProvider provider) {
        // ModelCatalogEntity has a protected no-args constructor for Hibernate and no public
        // constructor — use reflection to populate the minimum fields the router reads.
        try {
            java.lang.reflect.Constructor<ModelCatalogEntity> noArgs =
                    ModelCatalogEntity.class.getDeclaredConstructor();
            noArgs.setAccessible(true);
            ModelCatalogEntity model = noArgs.newInstance();
            setField(model, "modelId", modelId);
            setField(model, "provider", provider);
            setField(model, "displayName", "Display " + modelId);
            setField(model, "createdAt", FIXED_INSTANT);
            setField(model, "updatedAt", FIXED_INSTANT);
            setField(model, "recommended", true);
            setField(model, "verificationStatus", ModelVerificationStatus.VERIFIED);
            return model;
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new IllegalStateException(
                    "Failed to fabricate ModelCatalogEntity fixture", reflectiveOperationException);
        }
    }

    private static void setField(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
