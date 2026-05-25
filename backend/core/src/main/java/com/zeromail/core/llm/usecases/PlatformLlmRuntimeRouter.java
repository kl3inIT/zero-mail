package com.zeromail.core.llm.usecases;

import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.routing.LlmRouteResolver;
import com.zeromail.core.llm.routing.LlmRuntimeTask;
import com.zeromail.core.llm.routing.PlatformLlmRouteCredentialResolver;
import com.zeromail.core.llm.routing.PlatformLlmRouteCredentials;
import com.zeromail.core.llm.routing.ResolvedLlmRoute;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class PlatformLlmRuntimeRouter {

    private static final String OPENAI_FORMAT = "OPENAI_FORMAT";
    private static final String ANTHROPIC_FORMAT = "ANTHROPIC_FORMAT";
    private static final String GOOGLE_FORMAT = "GOOGLE_FORMAT";

    private final ZeroMailLlmProperties llmProperties;
    private final LlmRouteResolver routeResolver;
    private final PlatformLlmRouteCredentialResolver routeCredentialResolver;

    public PlatformLlmRuntimeRouter(
            ZeroMailCoreProperties zeroMailCoreProperties,
            ObjectProvider<LlmRouteResolver> routeResolverProvider,
            ObjectProvider<PlatformLlmRouteCredentialResolver> routeCredentialResolverProvider) {
        this.llmProperties = zeroMailCoreProperties.llm().platform();
        this.routeResolver = routeResolverProvider.getIfAvailable();
        this.routeCredentialResolver = routeCredentialResolverProvider.getIfAvailable();
    }

    public List<ResolvedLlmProviderCredential> resolveRoutes(
            LlmRuntimeTask runtimeTask, String fallbackModel) {
        if (routeResolver == null) {
            return List.of(fallbackCredential(modelOrFallback(fallbackModel)));
        }

        List<ResolvedLlmRoute> routes = routeResolver.resolve(runtimeTask);
        if (routes.isEmpty()) {
            return List.of(fallbackCredential(modelOrFallback(fallbackModel)));
        }

        List<ResolvedLlmProviderCredential> resolvedRoutes =
                routes.stream().map(this::resolveAdminRoute).flatMap(Optional::stream).toList();
        if (resolvedRoutes.isEmpty()) {
            throw new IllegalStateException(
                    "No platform LLM route credentials configured for " + runtimeTask.id());
        }
        return resolvedRoutes;
    }

    private Optional<ResolvedLlmProviderCredential> resolveAdminRoute(ResolvedLlmRoute route) {
        if (route.keyId() == null || routeCredentialResolver == null) {
            return Optional.empty();
        }
        return routeCredentialResolver
                .resolve(route.providerId(), route.keyId())
                .map(credentials -> fromRouteCredentials(route, credentials));
    }

    private ResolvedLlmProviderCredential fromRouteCredentials(
            ResolvedLlmRoute route, PlatformLlmRouteCredentials routeCredentials) {
        return new ResolvedLlmProviderCredential(
                route.providerId(),
                route.modelId(),
                new LlmProviderCredential(
                        routeCredentials.providerId(),
                        routeCredentials.keyFormat(),
                        routeCredentials.baseUrl(),
                        routeCredentials.plaintextKey(),
                        LlmCredentialSource.PLATFORM),
                routeCredentials.providerSecretVersion(),
                routeCredentials.providerCatalogVersion());
    }

    private ResolvedLlmProviderCredential fallbackCredential(String modelId) {
        String providerId = llmProperties.provider();
        return new ResolvedLlmProviderCredential(
                providerId,
                modelId,
                new LlmProviderCredential(
                        providerId,
                        fallbackKeyFormat(providerId, llmProperties.baseUrl()),
                        llmProperties.baseUrl(),
                        llmProperties.apiKey().getBytes(StandardCharsets.UTF_8),
                        LlmCredentialSource.PLATFORM),
                0,
                1);
    }

    private String modelOrFallback(String requestedFallbackModel) {
        if (requestedFallbackModel != null && !requestedFallbackModel.isBlank()) {
            return requestedFallbackModel.trim();
        }
        return llmProperties.compileModel();
    }

    private static String fallbackKeyFormat(String providerId, String baseUrl) {
        String normalizedProvider = providerId == null ? "" : providerId.toUpperCase(Locale.ROOT);
        if ("ANTHROPIC".equals(normalizedProvider)) {
            return ANTHROPIC_FORMAT;
        }
        if ("GOOGLE".equals(normalizedProvider) || "GOOGLE_GENAI".equals(normalizedProvider)) {
            return GOOGLE_FORMAT;
        }
        return OPENAI_FORMAT;
    }
}
