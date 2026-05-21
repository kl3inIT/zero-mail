package com.zeromail.core.admin.cat.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.ModelVerificationStatus;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.cat.persistence.FeatureDefaultProviderRepository;
import com.zeromail.core.admin.cat.persistence.FeatureTierModelRepository;
import com.zeromail.core.admin.cat.persistence.ModelCatalogEntity;
import com.zeromail.core.admin.cat.persistence.ModelCatalogRepository;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Invariant: {@link FeatureDefaultTierService#assign} rejects four classes of bad input — empty
 * model list, duplicate ids, provider/model mismatch, and ineligible models (deprecated / UNTESTED
 * / FAILED). These are the bounds the router relies on (LlmRouter assumes every model in a tier
 * already belongs to the right provider and is VERIFIED/STALE — Phase 8 invariant).
 *
 * <p>Test slice: plain JUnit + Mockito stubs on the four collaborators. No Spring context. Per
 * TESTING.md §5, internal repositories are not mocked when the test exercises a DB-side invariant;
 * this test pins use-case validation that runs entirely in the Java domain (before any SQL would
 * fire), so call-recorded stubs are the right level of slice.
 */
class FeatureDefaultTierServiceValidationTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000fee");
    private static final String REQUEST_IP = "10.0.0.1";
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000abcdef");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-22T10:00:00Z");

    private FeatureDefaultTierService service;

    void wireService(ModelCatalogRepository modelCatalogRepository) {
        FeatureDefaultProviderRepository featureDefaultProviderRepository =
                mock(FeatureDefaultProviderRepository.class);
        FeatureTierModelRepository featureTierModelRepository =
                mock(FeatureTierModelRepository.class);
        AdminAuditWriter adminAuditWriter = mock(AdminAuditWriter.class);
        when(featureDefaultProviderRepository.findBinding(any(), any()))
                .thenReturn(Optional.empty());
        service =
                new FeatureDefaultTierService(
                        featureDefaultProviderRepository,
                        featureTierModelRepository,
                        modelCatalogRepository,
                        adminAuditWriter,
                        Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    private static AdminUser admin() {
        return new AdminUser(ADMIN_ID, "ops@zeromail.test", AdminStatus.ACTIVE, Optional.empty());
    }

    private static ModelCatalogRepository catalogWithFixtures() {
        ModelCatalogRepository catalog = mock(ModelCatalogRepository.class);
        // Five canonical fixtures used across the @Nested classes.
        when(catalog.findById("m1"))
                .thenReturn(
                        Optional.of(
                                model(
                                        "m1",
                                        LlmProvider.OPENROUTER,
                                        ModelVerificationStatus.VERIFIED,
                                        false)));
        when(catalog.findById("m2"))
                .thenReturn(
                        Optional.of(
                                model(
                                        "m2",
                                        LlmProvider.OPENROUTER,
                                        ModelVerificationStatus.VERIFIED,
                                        false)));
        when(catalog.findById("m3"))
                .thenReturn(
                        Optional.of(
                                model(
                                        "m3",
                                        LlmProvider.OPENAI,
                                        ModelVerificationStatus.VERIFIED,
                                        false)));
        when(catalog.findById("m4"))
                .thenReturn(
                        Optional.of(
                                model(
                                        "m4",
                                        LlmProvider.OPENROUTER,
                                        ModelVerificationStatus.FAILED,
                                        false)));
        when(catalog.findById("m5"))
                .thenReturn(
                        Optional.of(
                                model(
                                        "m5",
                                        LlmProvider.OPENROUTER,
                                        ModelVerificationStatus.UNTESTED,
                                        false)));
        return catalog;
    }

    @Nested
    @DisplayName("EmptyList rejection")
    class EmptyList {

        @Test
        @DisplayName("assign(...) with empty model list throws EmptyTierModelListException")
        void rejects_empty_model_list() {
            wireService(catalogWithFixtures());
            AdminContext.run(
                    admin(),
                    () ->
                            assertThatThrownBy(
                                            () ->
                                                    service.assign(
                                                            Feature.TRIAGE,
                                                            RoutingTier.PRIMARY,
                                                            LlmProvider.OPENROUTER,
                                                            List.of(),
                                                            REQUEST_IP,
                                                            REQUEST_ID))
                                    .isInstanceOf(
                                            FeatureDefaultTierService.EmptyTierModelListException
                                                    .class));
        }
    }

    @Nested
    @DisplayName("DuplicateIds rejection")
    class DuplicateIds {

        @Test
        @DisplayName("assign(..., [m1, m1]) throws DuplicateTierModelException")
        void rejects_duplicate_model_ids() {
            wireService(catalogWithFixtures());
            AdminContext.run(
                    admin(),
                    () ->
                            assertThatThrownBy(
                                            () ->
                                                    service.assign(
                                                            Feature.TRIAGE,
                                                            RoutingTier.PRIMARY,
                                                            LlmProvider.OPENROUTER,
                                                            List.of("m1", "m1"),
                                                            REQUEST_IP,
                                                            REQUEST_ID))
                                    .isInstanceOf(
                                            FeatureDefaultTierService.DuplicateTierModelException
                                                    .class));
        }
    }

    @Nested
    @DisplayName("ProviderMismatch rejection")
    class ProviderMismatch {

        @Test
        @DisplayName(
                "assign(OPENROUTER, [m1@OpenRouter, m3@OpenAI]) throws ModelProviderMismatchException")
        void rejects_model_with_foreign_provider() {
            wireService(catalogWithFixtures());
            AdminContext.run(
                    admin(),
                    () ->
                            assertThatThrownBy(
                                            () ->
                                                    service.assign(
                                                            Feature.TRIAGE,
                                                            RoutingTier.PRIMARY,
                                                            LlmProvider.OPENROUTER,
                                                            List.of("m1", "m3"),
                                                            REQUEST_IP,
                                                            REQUEST_ID))
                                    .isInstanceOf(
                                            FeatureDefaultTierService.ModelProviderMismatchException
                                                    .class)
                                    // Message contains the offending id to aid ops debugging
                                    // (without asserting exact wording — TESTING.md §2).
                                    .hasMessageContaining("m3"));
        }
    }

    @Nested
    @DisplayName("IneligibleModel rejection")
    class IneligibleModel {

        @Test
        @DisplayName(
                "assign(..., [m1, m4(FAILED)]) and [..., m5(UNTESTED)] both throw"
                        + " ModelNotEligibleException")
        void rejects_failed_and_untested_models() {
            wireService(catalogWithFixtures());
            AdminContext.run(
                    admin(),
                    () -> {
                        assertThatThrownBy(
                                        () ->
                                                service.assign(
                                                        Feature.TRIAGE,
                                                        RoutingTier.PRIMARY,
                                                        LlmProvider.OPENROUTER,
                                                        List.of("m1", "m4"),
                                                        REQUEST_IP,
                                                        REQUEST_ID))
                                .isInstanceOf(
                                        FeatureDefaultTierService.ModelNotEligibleException.class)
                                .hasMessageContaining("m4");

                        assertThatThrownBy(
                                        () ->
                                                service.assign(
                                                        Feature.TRIAGE,
                                                        RoutingTier.PRIMARY,
                                                        LlmProvider.OPENROUTER,
                                                        List.of("m1", "m5"),
                                                        REQUEST_IP,
                                                        REQUEST_ID))
                                .isInstanceOf(
                                        FeatureDefaultTierService.ModelNotEligibleException.class)
                                .hasMessageContaining("m5");
                    });
        }
    }

    private static ModelCatalogEntity model(
            String modelId,
            LlmProvider provider,
            ModelVerificationStatus status,
            boolean deprecated) {
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
            setField(model, "verificationStatus", status);
            if (deprecated) {
                setField(model, "deprecatedAt", FIXED_INSTANT);
            }
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
