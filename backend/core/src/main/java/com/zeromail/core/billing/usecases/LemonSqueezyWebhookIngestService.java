package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodRepository;
import com.zeromail.core.billing.persistence.BillingPlanRepository;
import com.zeromail.core.billing.persistence.BillingWebhookEventEntity;
import com.zeromail.core.billing.persistence.BillingWebhookEventRepository;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class LemonSqueezyWebhookIngestService {

    private static final String PROCESSING_STATUS_RECEIVED = "RECEIVED";
    private static final String REDACTED = "[redacted]";
    private static final String PROVIDER_LEMON_SQUEEZY = "LEMON_SQUEEZY";
    private static final String PLAN_PERIOD_STATUS_ACTIVE = "ACTIVE";
    private static final Set<String> PLAN_PAYMENT_EVENTS = Set.of("order_created");

    private final BillingPlanRepository billingPlanRepository;
    private final BillingPlanPeriodRepository billingPlanPeriodRepository;
    private final BillingWebhookEventRepository billingWebhookEventRepository;
    private final CreditGrantService creditGrantService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public LemonSqueezyWebhookIngestService(
            BillingPlanRepository billingPlanRepository,
            BillingPlanPeriodRepository billingPlanPeriodRepository,
            BillingWebhookEventRepository billingWebhookEventRepository,
            CreditGrantService creditGrantService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate) {
        this.billingPlanRepository = billingPlanRepository;
        this.billingPlanPeriodRepository = billingPlanPeriodRepository;
        this.billingWebhookEventRepository = billingWebhookEventRepository;
        this.creditGrantService = creditGrantService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public LemonSqueezyWebhookIngestResult ingest(String rawPayload, String providerEventId) {
        String payload = rawPayload == null ? "" : rawPayload;
        JsonNode rootNode = readPayload(payload);
        JsonNode dataNode = dataNode(rootNode);
        String eventName = eventName(rootNode);
        String redactedPayloadJson = redactedPayloadJson(rootNode);
        if (!eventShouldBeStored(eventName)) {
            return new LemonSqueezyWebhookIngestResult(null, eventName, redactedPayloadJson);
        }

        Optional<UUID> tenantId = tenantId(rootNode);
        if (tenantId.isPresent()) {
            return ScopedValue.where(TenantContext.TENANT, tenantId.get().toString())
                    .call(
                            () ->
                                    ingestStoredEventInTransaction(
                                            payload,
                                            rootNode,
                                            dataNode,
                                            providerEventId,
                                            redactedPayloadJson));
        }
        return ingestStoredEventInTransaction(
                payload, rootNode, dataNode, providerEventId, redactedPayloadJson);
    }

    private LemonSqueezyWebhookIngestResult ingestStoredEventInTransaction(
            String payload,
            JsonNode rootNode,
            JsonNode dataNode,
            String providerEventId,
            String redactedPayloadJson) {
        return transactionTemplate.execute(
                ignoredTransactionStatus -> {
                    String payloadSha256 = sha256Hex(payload);
                    String dedupeKey = dedupeKey(providerEventId, payloadSha256);
                    Optional<BillingWebhookEventEntity> existingWebhookEvent =
                            billingWebhookEventRepository.findByDedupeKey(dedupeKey);
                    if (existingWebhookEvent.isPresent()) {
                        BillingWebhookEventEntity webhookEvent = existingWebhookEvent.get();
                        return new LemonSqueezyWebhookIngestResult(
                                webhookEvent.getId(),
                                webhookEvent.getEventName(),
                                webhookEvent.getPayloadJsonb());
                    }

                    BillingWebhookEventEntity webhookEvent =
                            billingWebhookEventRepository.save(
                                    eventEntity(
                                            rootNode,
                                            dataNode,
                                            providerEventId,
                                            payloadSha256,
                                            dedupeKey,
                                            redactedPayloadJson));
                    processWebhookEvent(webhookEvent, rootNode, dataNode);
                    return new LemonSqueezyWebhookIngestResult(
                            webhookEvent.getId(),
                            webhookEvent.getEventName(),
                            webhookEvent.getPayloadJsonb());
                });
    }

    private BillingWebhookEventEntity eventEntity(
            JsonNode rootNode,
            JsonNode dataNode,
            String providerEventId,
            String payloadSha256,
            String dedupeKey,
            String redactedPayloadJson) {
        return new BillingWebhookEventEntity(
                UUID.randomUUID(),
                blankToNull(providerEventId),
                dedupeKey,
                eventName(rootNode),
                tenantId(rootNode).orElse(null),
                orderId(dataNode).orElse(null),
                true,
                PROCESSING_STATUS_RECEIVED,
                Instant.now(),
                payloadSha256,
                redactedPayloadJson);
    }

    private void processWebhookEvent(
            BillingWebhookEventEntity webhookEvent, JsonNode rootNode, JsonNode dataNode) {
        webhookEvent.markProcessing();
        try {
            String eventName = eventName(rootNode);
            if (PLAN_PAYMENT_EVENTS.contains(eventName)) {
                OrderWebhookPayload orderWebhookPayload = orderWebhookPayload(rootNode, dataNode);
                if (!"orders".equals(orderWebhookPayload.dataType())) {
                    throw new WebhookProcessingException(
                            "unsupported_data_type:" + orderWebhookPayload.dataType());
                }
                createPlanPeriod(orderWebhookPayload, webhookEvent.getProviderEventId());
            }
            webhookEvent.markProcessed(Instant.now());
        } catch (WebhookProcessingException processingException) {
            webhookEvent.markFailed(Instant.now(), processingException.getMessage());
        }
    }

    private OrderWebhookPayload orderWebhookPayload(JsonNode rootNode, JsonNode dataNode) {
        JsonNode attributesNode = dataNode.path("attributes");
        JsonNode firstOrderItemNode = attributesNode.path("first_order_item");
        return new OrderWebhookPayload(
                eventName(rootNode),
                dataNode.path("type").asString(),
                tenantId(rootNode),
                longValue(dataNode.path("id")).or(() -> longValue(attributesNode.path("order_id"))),
                longValue(attributesNode.path("customer_id")),
                longValue(attributesNode.path("checkout_id")),
                longValue(attributesNode.path("product_id"))
                        .or(() -> longValue(firstOrderItemNode.path("product_id"))),
                longValue(attributesNode.path("variant_id"))
                        .or(() -> longValue(firstOrderItemNode.path("variant_id"))),
                textValue(rootNode.path("meta").path("custom_data").path("plan")),
                textValue(attributesNode.path("status")),
                longValue(attributesNode.path("total")),
                textValue(attributesNode.path("currency")),
                instantValue(attributesNode.path("created_at")));
    }

    private void createPlanPeriod(OrderWebhookPayload orderWebhookPayload, String providerEventId) {
        long orderId =
                orderWebhookPayload
                        .orderId()
                        .orElseThrow(
                                () ->
                                        new WebhookProcessingException(
                                                "missing_lemon_squeezy_order_id"));
        String providerOrderId = Long.toString(orderId);
        BillingPlanEntity billingPlan = resolveBillingPlan(orderWebhookPayload);
        validatePaidOrder(orderWebhookPayload);
        UUID tenantId =
                orderWebhookPayload
                        .tenantId()
                        .orElseThrow(() -> new WebhookProcessingException("missing_tenant_id"));
        Instant paidAt = orderWebhookPayload.createdAt().orElse(Instant.now());

        BillingPlanPeriodEntity billingPlanPeriod =
                billingPlanPeriodRepository
                        .findByProviderOrderId(providerOrderId)
                        .orElseGet(
                                () ->
                                        createPlanPeriod(
                                                tenantId,
                                                billingPlan,
                                                orderWebhookPayload,
                                                providerEventId,
                                                providerOrderId,
                                                paidAt));

        expireOverlappingPlanPeriods(billingPlanPeriod);
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> creditGrantService.resetCurrentPlanAllowanceCredits(tenantId));
    }

    private BillingPlanPeriodEntity createPlanPeriod(
            UUID tenantId,
            BillingPlanEntity billingPlan,
            OrderWebhookPayload orderWebhookPayload,
            String providerEventId,
            String providerOrderId,
            Instant paidAt) {
        Instant expiresAt =
                paidAt.atZone(CreditGrantService.PLAN_ALLOWANCE_RESET_ZONE)
                        .plusMonths(1)
                        .toInstant();
        BillingPlanPeriodEntity billingPlanPeriod =
                new BillingPlanPeriodEntity(
                        UUID.randomUUID(),
                        tenantId,
                        billingPlan.getId(),
                        PLAN_PERIOD_STATUS_ACTIVE,
                        PROVIDER_LEMON_SQUEEZY,
                        providerOrderId,
                        orderWebhookPayload.checkoutId().map(Object::toString).orElse(null),
                        blankToNull(providerEventId),
                        paidAt.truncatedTo(ChronoUnit.MILLIS),
                        expiresAt.truncatedTo(ChronoUnit.MILLIS),
                        paidAt.truncatedTo(ChronoUnit.MILLIS),
                        orderWebhookPayload.totalAmount().orElse(billingPlan.getPriceVnd()),
                        orderWebhookPayload
                                .currency()
                                .map(currency -> currency.trim().toUpperCase(Locale.ROOT))
                                .orElse(billingPlan.getCurrency()),
                        orderWebhookPayload.customerId().orElse(null),
                        orderWebhookPayload.productId().orElse(null),
                        orderWebhookPayload.variantId().orElse(null));
        return billingPlanPeriodRepository.save(billingPlanPeriod);
    }

    private void expireOverlappingPlanPeriods(BillingPlanPeriodEntity currentPlanPeriod) {
        billingPlanPeriodRepository
                .findOverlappingActiveTenantPlanPeriods(
                        currentPlanPeriod.getTenantId(),
                        currentPlanPeriod.getId(),
                        currentPlanPeriod.getEffectiveAt(),
                        currentPlanPeriod.getExpiresAt())
                .forEach(
                        overlappingPlanPeriod -> {
                            overlappingPlanPeriod.markExpired();
                            billingPlanPeriodRepository.save(overlappingPlanPeriod);
                        });
    }

    private void validatePaidOrder(OrderWebhookPayload orderWebhookPayload) {
        String orderStatus =
                orderWebhookPayload
                        .status()
                        .map(status -> status.trim().toLowerCase(Locale.ROOT))
                        .orElseThrow(() -> new WebhookProcessingException("missing_order_status"));
        if (!"paid".equals(orderStatus)) {
            throw new WebhookProcessingException("order_not_paid:" + orderStatus);
        }
    }

    private BillingPlanEntity resolveBillingPlan(OrderWebhookPayload orderWebhookPayload) {
        Optional<BillingPlanEntity> billingPlan =
                orderWebhookPayload
                        .variantId()
                        .flatMap(billingPlanRepository::findByLemonSqueezyVariantId);
        if (billingPlan.isPresent()) {
            return billingPlan.get();
        }
        return orderWebhookPayload
                .planCode()
                .map(planCode -> planCode.toUpperCase(Locale.ROOT))
                .flatMap(billingPlanRepository::findByCode)
                .filter(BillingPlanEntity::isActive)
                .orElseThrow(() -> new WebhookProcessingException("billing_plan_not_found"));
    }

    private boolean eventShouldBeStored(String eventName) {
        return PLAN_PAYMENT_EVENTS.contains(eventName);
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception parseFailure) {
            ObjectNode fallbackNode = objectMapper.createObjectNode();
            fallbackNode.put("parse_error", "invalid_json");
            fallbackNode.put("raw_payload_sha256", sha256Hex(payload));
            return fallbackNode;
        }
    }

    private JsonNode dataNode(JsonNode rootNode) {
        JsonNode dataNode = rootNode.path("data");
        return dataNode.isObject() ? dataNode : rootNode;
    }

    private String eventName(JsonNode rootNode) {
        String eventName = rootNode.path("meta").path("event_name").asString();
        return eventName == null || eventName.isBlank() ? "unknown" : eventName;
    }

    private Optional<UUID> tenantId(JsonNode rootNode) {
        String tenantId = rootNode.path("meta").path("custom_data").path("tenant_id").asString();
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(tenantId));
        } catch (IllegalArgumentException invalidTenantId) {
            return Optional.empty();
        }
    }

    private Optional<Long> orderId(JsonNode dataNode) {
        if ("orders".equals(dataNode.path("type").asString())) {
            return longValue(dataNode.path("id"))
                    .or(() -> longValue(dataNode.path("attributes").path("order_id")));
        }
        return Optional.empty();
    }

    private Optional<String> textValue(JsonNode jsonNode) {
        if (jsonNode.isMissingNode() || jsonNode.isNull()) {
            return Optional.empty();
        }
        String value = jsonNode.asString();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private Optional<Long> longValue(JsonNode jsonNode) {
        if (jsonNode.isMissingNode() || jsonNode.isNull()) {
            return Optional.empty();
        }
        if (jsonNode.isNumber()) {
            return Optional.of(jsonNode.asLong());
        }
        return textValue(jsonNode).flatMap(this::parseLong);
    }

    private Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException invalidLongValue) {
            return Optional.empty();
        }
    }

    private Optional<Instant> instantValue(JsonNode jsonNode) {
        Optional<String> value = textValue(jsonNode);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.get()));
        } catch (RuntimeException invalidInstant) {
            return Optional.empty();
        }
    }

    private String redactedPayloadJson(JsonNode rootNode) {
        try {
            return objectMapper.writeValueAsString(redact(rootNode));
        } catch (Exception serializationFailure) {
            return "{\"redaction_error\":\"serialization_failed\"}";
        }
    }

    private JsonNode redact(JsonNode jsonNode) {
        if (jsonNode.isObject()) {
            ObjectNode redactedObject = objectMapper.createObjectNode();
            jsonNode.properties()
                    .forEach(
                            property -> {
                                String fieldName = property.getKey();
                                if (isSensitiveField(fieldName)) {
                                    redactedObject.put(fieldName, REDACTED);
                                } else {
                                    redactedObject.set(fieldName, redact(property.getValue()));
                                }
                            });
            return redactedObject;
        }
        if (jsonNode.isArray()) {
            ArrayNode redactedArray = objectMapper.createArrayNode();
            jsonNode.forEach(elementNode -> redactedArray.add(redact(elementNode)));
            return redactedArray;
        }
        return jsonNode;
    }

    private boolean isSensitiveField(String fieldName) {
        String normalizedFieldName = fieldName.toLowerCase(Locale.ROOT);
        return normalizedFieldName.equals("email")
                || normalizedFieldName.endsWith("_email")
                || normalizedFieldName.equals("billing_address")
                || normalizedFieldName.startsWith("card_");
    }

    private String dedupeKey(String providerEventId, String payloadSha256) {
        String normalizedProviderEventId = blankToNull(providerEventId);
        return normalizedProviderEventId == null
                ? "payload_sha256:" + payloadSha256
                : "provider_event_id:" + normalizedProviderEventId;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception hashingFailure) {
            throw new IllegalStateException("SHA-256 digest is unavailable", hashingFailure);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record OrderWebhookPayload(
            String eventName,
            String dataType,
            Optional<UUID> tenantId,
            Optional<Long> orderId,
            Optional<Long> customerId,
            Optional<Long> checkoutId,
            Optional<Long> productId,
            Optional<Long> variantId,
            Optional<String> planCode,
            Optional<String> status,
            Optional<Long> totalAmount,
            Optional<String> currency,
            Optional<Instant> createdAt) {}

    private static class WebhookProcessingException extends RuntimeException {

        WebhookProcessingException(String message) {
            super(message);
        }
    }
}
