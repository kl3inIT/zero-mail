package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.usecases.ByokValidateResult;
import java.net.SocketTimeoutException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Provider-aware HTTP gateway that probes an upstream LLM provider's {@code /models} endpoint to
 * validate a BYOK API key + model id. Keeps provider-specific URL/header/JSON knowledge out of
 * {@code core.llm.usecases.ByokService} so the use-case service does not know HTTP shapes (project
 * rule: raw HTTP LLM calls / vendor SDK usage stays inside {@code core.llm.gateway.springai}).
 */
@Component
public class ByokValidationGateway {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient.Builder restClientBuilder;

    public ByokValidationGateway(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public ByokValidateResult validate(
            BYOKProvider provider, String canonicalEndpoint, String model, String apiKey) {
        try {
            return switch (provider) {
                case ANTHROPIC -> probeAnthropic(canonicalEndpoint, apiKey, model);
                case DEEPSEEK -> probeOpenAi(canonicalEndpoint, apiKey, model);
                case GOOGLE_GENAI -> probeGoogleGenAi(canonicalEndpoint, apiKey, model);
                case OPENAI -> probeOpenAi(canonicalEndpoint, apiKey, model);
            };
        } catch (RestClientResponseException upstreamRejection) {
            return new ByokValidateResult(
                    false, null, reasonForUpstreamRejection(provider, upstreamRejection));
        } catch (ResourceAccessException resourceAccessFailure) {
            return new ByokValidateResult(
                    false,
                    null,
                    isTimeout(resourceAccessFailure) ? "timeout" : "connection_failed");
        } catch (RestClientException restClientFailure) {
            return new ByokValidateResult(false, null, "connection_failed");
        }
    }

    private ByokValidateResult probeOpenAi(String canonicalEndpoint, String apiKey, String model) {
        ModelsResponse response =
                restClientBuilder
                        .build()
                        .get()
                        .uri(joinPath(canonicalEndpoint, "models"))
                        .headers(headers -> headers.setBearerAuth(apiKey))
                        .retrieve()
                        .body(ModelsResponse.class);
        List<String> modelIds =
                response == null || response.data() == null
                        ? List.of()
                        : response.data().stream().map(ModelResource::id).toList();
        if (!modelIds.isEmpty() && !modelIds.contains(model)) {
            return new ByokValidateResult(false, modelIds, "model_not_found");
        }
        return new ByokValidateResult(true, modelIds, null);
    }

    private ByokValidateResult probeAnthropic(
            String canonicalEndpoint, String apiKey, String model) {
        ModelsResponse response =
                restClientBuilder
                        .build()
                        .get()
                        .uri(joinPath(canonicalEndpoint, "models"))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .retrieve()
                        .body(ModelsResponse.class);
        List<String> modelIds =
                response == null || response.data() == null
                        ? List.of()
                        : response.data().stream().map(ModelResource::id).toList();
        if (!modelIds.isEmpty() && !modelIds.contains(model)) {
            return new ByokValidateResult(false, modelIds, "model_not_found");
        }
        return new ByokValidateResult(true, modelIds.isEmpty() ? List.of(model) : modelIds, null);
    }

    private ByokValidateResult probeGoogleGenAi(
            String canonicalEndpoint, String apiKey, String model) {
        GoogleModelsResponse response =
                restClientBuilder
                        .build()
                        .get()
                        .uri(joinPath(canonicalEndpoint, "models"))
                        .header("x-goog-api-key", apiKey)
                        .retrieve()
                        .body(GoogleModelsResponse.class);
        List<String> modelIds =
                response == null || response.models() == null
                        ? List.of()
                        : response.models().stream()
                                .filter(ByokValidationGateway::supportsGoogleGenerateContent)
                                .map(GoogleModelResource::name)
                                .filter(modelName -> modelName != null && !modelName.isBlank())
                                .map(ByokValidationGateway::googleModelId)
                                .toList();
        if (!modelIds.isEmpty() && !modelIds.contains(model)) {
            return new ByokValidateResult(false, modelIds, "model_not_found");
        }
        return new ByokValidateResult(true, modelIds, null);
    }

    private static String reasonForUpstreamRejection(
            BYOKProvider provider, RestClientResponseException upstreamRejection) {
        int upstreamStatus = upstreamRejection.getStatusCode().value();
        if (upstreamStatus == 404) {
            return "endpoint_rejected";
        }
        if (provider == BYOKProvider.ANTHROPIC && upstreamStatus == 400) {
            return "model_not_found";
        }
        if (upstreamStatus == 422) {
            return "model_not_found";
        }
        return "upstream_rejected";
    }

    private static String joinPath(String canonicalEndpoint, String suffix) {
        return canonicalEndpoint.replaceAll("/+$", "") + "/" + suffix.replaceAll("^/+", "");
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable currentThrowable = throwable;
        while (currentThrowable != null) {
            if (currentThrowable instanceof SocketTimeoutException) {
                return true;
            }
            currentThrowable = currentThrowable.getCause();
        }
        return false;
    }

    private static boolean supportsGoogleGenerateContent(GoogleModelResource modelResource) {
        return modelResource.supportedGenerationMethods() == null
                || modelResource.supportedGenerationMethods().contains("generateContent");
    }

    private static String googleModelId(String modelName) {
        return modelName.startsWith("models/")
                ? modelName.substring("models/".length())
                : modelName;
    }

    private record ModelsResponse(List<ModelResource> data) {}

    private record ModelResource(String id) {}

    private record GoogleModelsResponse(List<GoogleModelResource> models) {}

    private record GoogleModelResource(String name, List<String> supportedGenerationMethods) {}
}
