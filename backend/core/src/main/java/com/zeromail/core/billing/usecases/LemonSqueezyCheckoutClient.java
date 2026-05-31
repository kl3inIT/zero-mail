package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.config.BillingProperties;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Creates Lemon Squeezy hosted checkout sessions through the JSON:API endpoint and returns the
 * hosted checkout URL from {@code data.attributes.url}.
 *
 * <p>The API request carries tenant metadata under {@code checkout_data.custom.tenant_id}; Lemon
 * Squeezy echoes that value under webhook {@code meta.custom_data.tenant_id}, which lets the order
 * webhook handler bind the one-time payment back to the tenant.
 */
@Component
public class LemonSqueezyCheckoutClient {

    private static final MediaType JSON_API_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.api+json");

    private final BillingProperties.LemonSqueezyProperties lemonSqueezy;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public LemonSqueezyCheckoutClient(
            BillingProperties billingProperties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this.lemonSqueezy = billingProperties.lemonSqueezy();
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    public LemonSqueezyCheckoutCreation createCheckout(
            BillingPlanEntity plan, UUID tenantId, String userEmail) {
        if (plan.getLemonSqueezyVariantId() == null) {
            return failure("{}", null, "missing_lemon_squeezy_variant_id");
        }
        if (!lemonSqueezy.isConfigured()) {
            return failure("{}", null, "lemon_squeezy_not_configured");
        }

        Map<String, Object> requestBody = createCheckoutRequest(plan, tenantId, userEmail);
        String requestJsonb = writeJson(requestBody);
        try {
            String responseBody =
                    restClientBuilder
                            .clone()
                            .baseUrl(lemonSqueezy.apiBaseUrl().toString())
                            .build()
                            .post()
                            .uri("/checkouts")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + lemonSqueezy.apiKey())
                            .accept(JSON_API_MEDIA_TYPE)
                            .contentType(JSON_API_MEDIA_TYPE)
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);
            return checkoutCreation(requestJsonb, responseBody);
        } catch (RestClientException | IllegalArgumentException checkoutCreationFailure) {
            return failure(requestJsonb, null, checkoutCreationFailure.getClass().getSimpleName());
        }
    }

    private Map<String, Object> createCheckoutRequest(
            BillingPlanEntity plan, UUID tenantId, String userEmail) {
        Map<String, Object> checkoutData = new LinkedHashMap<>();
        if (userEmail != null && !userEmail.isBlank()) {
            checkoutData.put("email", userEmail);
        }
        checkoutData.put(
                "custom",
                Map.of(
                        "tenant_id",
                        tenantId.toString(),
                        "plan",
                        plan.getCode(),
                        "credits",
                        Integer.toString(plan.getMonthlyCreditAllowance())));

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("checkout_data", checkoutData);
        attributes.put("product_options", productOptions(plan));
        attributes.put("test_mode", lemonSqueezy.testMode());

        return Map.of(
                "data",
                Map.of(
                        "type",
                        "checkouts",
                        "attributes",
                        attributes,
                        "relationships",
                        Map.of(
                                "store",
                                relationship("stores", lemonSqueezy.storeId().toString()),
                                "variant",
                                relationship(
                                        "variants", plan.getLemonSqueezyVariantId().toString()))));
    }

    private Map<String, Object> relationship(String type, String id) {
        return Map.of("data", Map.of("type", type, "id", id));
    }

    private Map<String, Object> productOptions(BillingPlanEntity plan) {
        return Map.of(
                "name",
                "ZeroMail " + plan.getDisplayName(),
                "description",
                planDescription(plan),
                "enabled_variants",
                List.of(plan.getLemonSqueezyVariantId()));
    }

    private String planDescription(BillingPlanEntity plan) {
        return plan.getDisplayName()
                + " plan with "
                + plan.getMonthlyCreditAllowance()
                + " AI credits per month.";
    }

    private LemonSqueezyCheckoutCreation checkoutCreation(
            String requestJsonb, String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return failure(requestJsonb, null, "empty_response_body");
        }
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            String checkoutUrl = rootNode.path("data").path("attributes").path("url").asString();
            String checkoutId = rootNode.path("data").path("id").asString();
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                return failure(requestJsonb, responseBody, "missing_checkout_url");
            }
            return new LemonSqueezyCheckoutCreation(
                    checkoutUrl,
                    checkoutId == null || checkoutId.isBlank() ? null : checkoutId,
                    requestJsonb,
                    responseBody,
                    null);
        } catch (Exception parsingFailure) {
            return failure(requestJsonb, responseBody, "invalid_response_json");
        }
    }

    private LemonSqueezyCheckoutCreation failure(
            String requestJsonb, String responseJsonb, String failureReason) {
        return new LemonSqueezyCheckoutCreation(
                null, null, requestJsonb, responseJsonb, failureReason);
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception serializationFailure) {
            return "{}";
        }
    }
}
