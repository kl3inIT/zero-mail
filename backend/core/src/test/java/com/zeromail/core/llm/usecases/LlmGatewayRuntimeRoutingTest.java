package com.zeromail.core.llm.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.config.LlmProperties.PlatformProperties;
import com.zeromail.core.llm.domain.ActionValidator;
import com.zeromail.core.llm.domain.AllowListedTools;
import com.zeromail.core.llm.gateway.sanitization.SanitizationPipeline;
import com.zeromail.core.llm.gateway.sanitization.Sanitizer;
import com.zeromail.core.llm.routing.LlmRouteResolver;
import com.zeromail.core.llm.routing.LlmRoutingTier;
import com.zeromail.core.llm.routing.LlmRuntimeTask;
import com.zeromail.core.llm.routing.PlatformLlmRouteCredentialResolver;
import com.zeromail.core.llm.routing.PlatformLlmRouteCredentials;
import com.zeromail.core.llm.routing.ResolvedLlmRoute;
import com.zeromail.core.tenant.TenantContext;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LlmGatewayRuntimeRoutingTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000146");

    @Test
    void compileRule_uses_rule_authoring_route_before_property_fallback() throws Exception {
        RecordingLlmModelClient modelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(
                                        new RawToolCall(
                                                "rule_compile",
                                                "{\"schemaVersion\":\"rules.v1\",\"displayName\":\"VIP\"}")),
                                new LlmUsage(3, 2, "stop")));
        LlmGateway gateway =
                gateway(
                        modelClient,
                        (_, _, _, _) ->
                                new SemanticIntentEvaluationResult(
                                        Map.of(), new LlmUsage(0, 0, "stop")),
                        routeResolver(
                                LlmRuntimeTask.RULE_AUTHORING, "openrouter/rule-authoring-admin"));

        RuleCompileGatewayResult result =
                underTenant(
                        () -> gateway.compileRule(CallSite.PREVIEW, "{\"sourceText\":\"VIP\"}"));

        assertThat(result.modelId()).isEqualTo("openrouter/rule-authoring-admin");
        assertThat(modelClient.lastRequest().model()).isEqualTo("openrouter/rule-authoring-admin");
    }

    @Test
    void compileRule_uses_admin_configured_provider_credentials_for_runtime_route()
            throws Exception {
        UUID keyId = UUID.fromString("00000000-0000-0000-0000-000000009001");
        RecordingLlmModelClient modelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(
                                        new RawToolCall(
                                                "rule_compile",
                                                "{\"schemaVersion\":\"rules.v1\",\"displayName\":\"VIP\"}")),
                                new LlmUsage(3, 2, "stop")));
        PlatformLlmRouteCredentials routeCredentials =
                routeCredentials(
                        "ROUTER_9R", keyId, "cx/gpt-5.5", "https://9router.zeromail.vn/v1");
        LlmGateway gateway =
                gateway(
                        modelClient,
                        (_, _, _, _) ->
                                new SemanticIntentEvaluationResult(
                                        Map.of(), new LlmUsage(0, 0, "stop")),
                        routeResolver(
                                LlmRuntimeTask.RULE_AUTHORING, "ROUTER_9R", "cx/gpt-5.5", keyId),
                        credentialResolver(routeCredentials));

        RuleCompileGatewayResult result =
                underTenant(
                        () -> gateway.compileRule(CallSite.PREVIEW, "{\"sourceText\":\"VIP\"}"));

        assertThat(result.modelId()).isEqualTo("cx/gpt-5.5");
        assertThat(modelClient.lastRouteCredentials()).isNotNull();
        assertThat(modelClient.lastRouteCredentials().providerId()).isEqualTo("ROUTER_9R");
        assertThat(modelClient.lastRouteCredentials().keyId()).isEqualTo(keyId);
        assertThat(modelClient.lastRouteCredentials().baseUrl())
                .isEqualTo("https://9router.zeromail.vn/v1");
    }

    @Test
    void draftGeneration_uses_draft_generation_route_before_property_fallback() throws Exception {
        RecordingLlmModelClient modelClient =
                new RecordingLlmModelClient(
                        new LlmChatResult(
                                List.of(
                                        new RawToolCall(
                                                "save_draft",
                                                "{\"body\":\"Thanks, I will review this.\"}")),
                                new LlmUsage(4, 3, "stop")));
        LlmGateway gateway =
                gateway(
                        modelClient,
                        (_, _, _, _) ->
                                new SemanticIntentEvaluationResult(
                                        Map.of(), new LlmUsage(0, 0, "stop")),
                        routeResolver(
                                LlmRuntimeTask.DRAFT_GENERATION,
                                "openrouter/draft-generation-admin"));

        ToolCallResult result =
                underTenant(
                        () ->
                                gateway.chatForDraft(
                                        CallSite.DRAFT,
                                        new SanitizationContext("inbound", 1, false, null),
                                        "",
                                        List.of(),
                                        "Hello"));

        assertThat(result.args()).containsEntry("body", "Thanks, I will review this.");
        assertThat(modelClient.lastRequest().model())
                .isEqualTo("openrouter/draft-generation-admin");
    }

    @Test
    void semanticEvaluation_uses_preview_and_runtime_routes() throws Exception {
        RecordingSemanticIntentEvaluator semanticEvaluator = new RecordingSemanticIntentEvaluator();
        LlmGateway gateway =
                gateway(
                        _ -> {
                            throw new AssertionError(
                                    "semantic evaluation must not call chat client");
                        },
                        semanticEvaluator,
                        routeResolver(
                                Map.of(
                                        LlmRuntimeTask.RULE_PREVIEW_SEMANTIC,
                                        "openrouter/rule-preview-admin",
                                        LlmRuntimeTask.TRIAGE_SEMANTIC,
                                        "openrouter/triage-semantic-admin")));

        underTenant(
                () ->
                        gateway.evaluateSemanticIntents(
                                CallSite.PREVIEW,
                                "message",
                                List.of(new SemanticIntentRequest("n1", "asks for quote"))));

        assertThat(semanticEvaluator.lastModelId()).isEqualTo("openrouter/rule-preview-admin");

        underTenant(
                () ->
                        gateway.evaluateSemanticIntents(
                                CallSite.TRIAGE_PLATFORM_LLM,
                                "message",
                                List.of(new SemanticIntentRequest("n1", "asks for quote"))));

        assertThat(semanticEvaluator.lastModelId()).isEqualTo("openrouter/triage-semantic-admin");
    }

    @Test
    void semanticEvaluation_uses_admin_configured_provider_credentials_for_runtime_route()
            throws Exception {
        UUID keyId = UUID.fromString("00000000-0000-0000-0000-000000009002");
        RecordingSemanticIntentEvaluator semanticEvaluator = new RecordingSemanticIntentEvaluator();
        PlatformLlmRouteCredentials routeCredentials =
                routeCredentials(
                        "ROUTER_9R", keyId, "cx/gpt-5.5", "https://9router.zeromail.vn/v1");
        LlmGateway gateway =
                gateway(
                        _ -> {
                            throw new AssertionError(
                                    "semantic evaluation must not call chat client");
                        },
                        semanticEvaluator,
                        routeResolver(
                                LlmRuntimeTask.RULE_PREVIEW_SEMANTIC,
                                "ROUTER_9R",
                                "cx/gpt-5.5",
                                keyId),
                        credentialResolver(routeCredentials));

        underTenant(
                () ->
                        gateway.evaluateSemanticIntents(
                                CallSite.PREVIEW,
                                "message",
                                List.of(new SemanticIntentRequest("n1", "asks for quote"))));

        assertThat(semanticEvaluator.lastModelId()).isEqualTo("cx/gpt-5.5");
        assertThat(semanticEvaluator.lastRouteCredentials()).isNotNull();
        assertThat(semanticEvaluator.lastRouteCredentials().providerId()).isEqualTo("ROUTER_9R");
        assertThat(semanticEvaluator.lastRouteCredentials().keyId()).isEqualTo(keyId);
        assertThat(semanticEvaluator.lastRouteCredentials().baseUrl())
                .isEqualTo("https://9router.zeromail.vn/v1");
    }

    @Test
    void platformRoute_walks_fallback_models_in_order() throws Exception {
        RecordingLlmModelClient modelClient =
                new RecordingLlmModelClient(
                        new IllegalStateException("first route failed"),
                        new LlmChatResult(
                                List.of(
                                        new RawToolCall(
                                                "rule_compile",
                                                "{\"schemaVersion\":\"rules.v1\",\"displayName\":\"Sales\"}")),
                                new LlmUsage(5, 1, "stop")));
        LlmGateway gateway =
                gateway(
                        modelClient,
                        (_, _, _, _) ->
                                new SemanticIntentEvaluationResult(
                                        Map.of(), new LlmUsage(0, 0, "stop")),
                        routeResolver(
                                LlmRuntimeTask.RULE_AUTHORING,
                                List.of("openrouter/primary-bad", "openrouter/fallback-good")));

        RuleCompileGatewayResult result =
                underTenant(
                        () -> gateway.compileRule(CallSite.PREVIEW, "{\"sourceText\":\"VIP\"}"));

        assertThat(result.modelId()).isEqualTo("openrouter/fallback-good");
        assertThat(modelClient.requestedModels())
                .containsExactly("openrouter/primary-bad", "openrouter/fallback-good");
    }

    private LlmGateway gateway(
            LlmModelClient modelClient,
            SemanticIntentEvaluator semanticIntentEvaluator,
            LlmRouteResolver routeResolver) {
        return gateway(modelClient, semanticIntentEvaluator, routeResolver, null);
    }

    private LlmGateway gateway(
            LlmModelClient modelClient,
            SemanticIntentEvaluator semanticIntentEvaluator,
            LlmRouteResolver routeResolver,
            PlatformLlmRouteCredentialResolver routeCredentialResolver) {
        return new LlmGatewayImpl(
                modelClient,
                semanticIntentEvaluator,
                new SanitizationPipeline(List.of(new FixedSanitizer())),
                llmProperties(),
                new AllowListedTools(),
                new ActionValidator(),
                ObservationRegistry.create(),
                routeResolver,
                routeCredentialResolver);
    }

    private static <T> T underTenant(ThrowingSupplier<T> supplier) throws Exception {
        return ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString()).call(supplier::get);
    }

    private static PlatformProperties llmProperties() {
        return new PlatformProperties(
                "openai",
                "https://openrouter.ai/api/v1",
                "test-platform-key",
                "fallback-compile-model",
                "fallback-drift-model",
                "fallback-triage-model",
                "fallback-draft-model",
                null,
                null);
    }

    private static LlmRouteResolver routeResolver(LlmRuntimeTask task, String modelId) {
        return routeResolver(Map.of(task, modelId));
    }

    private static LlmRouteResolver routeResolver(LlmRuntimeTask task, List<String> modelIds) {
        return routeResolver(Map.of(task, modelIds));
    }

    private static LlmRouteResolver routeResolver(
            LlmRuntimeTask task, String providerId, String modelId, UUID keyId) {
        return requestedTask ->
                requestedTask == task
                        ? List.of(
                                new ResolvedLlmRoute(
                                        task,
                                        LlmRoutingTier.PRIMARY,
                                        providerId,
                                        modelId,
                                        keyId,
                                        1))
                        : List.of();
    }

    private static LlmRouteResolver routeResolver(Map<LlmRuntimeTask, ?> modelsByTask) {
        Map<LlmRuntimeTask, List<ResolvedLlmRoute>> routesByTask =
                new EnumMap<>(LlmRuntimeTask.class);
        for (Map.Entry<LlmRuntimeTask, ?> entry : modelsByTask.entrySet()) {
            List<String> modelIds =
                    entry.getValue() instanceof List<?> modelList
                            ? modelList.stream().map(Object::toString).toList()
                            : List.of(entry.getValue().toString());
            routesByTask.put(
                    entry.getKey(),
                    modelIds.stream()
                            .map(
                                    modelId ->
                                            new ResolvedLlmRoute(
                                                    entry.getKey(),
                                                    LlmRoutingTier.PRIMARY,
                                                    "OPENROUTER",
                                                    modelId,
                                                    UUID.randomUUID(),
                                                    1))
                            .toList());
        }
        return task -> routesByTask.getOrDefault(task, List.of());
    }

    private static PlatformLlmRouteCredentialResolver credentialResolver(
            PlatformLlmRouteCredentials routeCredentials) {
        return (providerId, keyId) ->
                providerId.equals(routeCredentials.providerId())
                                && keyId.equals(routeCredentials.keyId())
                        ? Optional.of(routeCredentials)
                        : Optional.empty();
    }

    private static PlatformLlmRouteCredentials routeCredentials(
            String providerId, UUID keyId, String modelId, String baseUrl) {
        return new PlatformLlmRouteCredentials(
                providerId,
                keyId,
                ("key-for-" + modelId).getBytes(StandardCharsets.UTF_8),
                "OPENAI_FORMAT",
                baseUrl,
                1,
                1);
    }

    private static final class FixedSanitizer implements Sanitizer {

        @Override
        public SanitizationContext apply(SanitizationContext context) {
            return new SanitizationContext("sanitized-message", 3, false, null);
        }
    }

    private static final class RecordingSemanticIntentEvaluator implements SemanticIntentEvaluator {

        private final AtomicReference<String> lastModelId = new AtomicReference<>();
        private final AtomicReference<PlatformLlmRouteCredentials> lastRouteCredentials =
                new AtomicReference<>();

        @Override
        public SemanticIntentEvaluationResult evaluate(
                CallSite callSite,
                String modelId,
                String sanitizedMessageContent,
                List<SemanticIntentRequest> intents) {
            lastModelId.set(modelId);
            return new SemanticIntentEvaluationResult(
                    Map.of(intents.getFirst().nodeId(), true), new LlmUsage(2, 1, "stop"));
        }

        @Override
        public SemanticIntentEvaluationResult evaluate(
                CallSite callSite,
                String modelId,
                PlatformLlmRouteCredentials routeCredentials,
                String sanitizedMessageContent,
                List<SemanticIntentRequest> intents) {
            lastRouteCredentials.set(routeCredentials);
            return evaluate(callSite, modelId, sanitizedMessageContent, intents);
        }

        private String lastModelId() {
            return lastModelId.get();
        }

        private PlatformLlmRouteCredentials lastRouteCredentials() {
            return lastRouteCredentials.get();
        }
    }

    private static final class RecordingLlmModelClient implements LlmModelClient {

        private final List<Object> outcomes;
        private final AtomicReference<LlmChatRequest> lastRequest = new AtomicReference<>();
        private final AtomicReference<PlatformLlmRouteCredentials> lastRouteCredentials =
                new AtomicReference<>();
        private final java.util.ArrayList<String> requestedModels = new java.util.ArrayList<>();
        private int callIndex;

        private RecordingLlmModelClient(Object... outcomes) {
            this.outcomes = List.of(outcomes);
        }

        @Override
        public LlmChatResult call(LlmChatRequest request) {
            lastRequest.set(request);
            requestedModels.add(request.model());
            Object outcome = outcomes.get(Math.min(callIndex, outcomes.size() - 1));
            callIndex++;
            if (outcome instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return (LlmChatResult) outcome;
        }

        @Override
        public LlmChatResult call(
                LlmChatRequest request, PlatformLlmRouteCredentials routeCredentials) {
            lastRouteCredentials.set(routeCredentials);
            return call(request);
        }

        private LlmChatRequest lastRequest() {
            return lastRequest.get();
        }

        private List<String> requestedModels() {
            return List.copyOf(requestedModels);
        }

        private PlatformLlmRouteCredentials lastRouteCredentials() {
            return lastRouteCredentials.get();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
