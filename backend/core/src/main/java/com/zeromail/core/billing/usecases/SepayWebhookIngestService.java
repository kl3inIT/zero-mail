package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.event.PlanUpgradePaymentCompleted;
import com.zeromail.core.billing.persistence.BillingBankTransferIntentEntity;
import com.zeromail.core.billing.persistence.BillingBankTransferIntentRepository;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanRepository;
import com.zeromail.core.billing.persistence.BillingWebhookEventEntity;
import com.zeromail.core.billing.persistence.BillingWebhookEventRepository;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SepayWebhookIngestService {

    private static final String PROVIDER_SEPAY = "SEPAY";
    private static final String PROCESSING_STATUS_RECEIVED = "RECEIVED";
    private static final String REDACTED = "[redacted]";
    private static final Pattern BANK_TRANSFER_CODE_PATTERN =
            Pattern.compile("[0-9A-HJKMNPQRSTVWXYZ]{8}");
    private static final DateTimeFormatter SEPAY_TRANSACTION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId SEPAY_TRANSACTION_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BillingBankTransferIntentRepository bankTransferIntentRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final BillingWebhookEventRepository billingWebhookEventRepository;
    private final ObjectMapper objectMapper;
    private final PaidPlanActivationService paidPlanActivationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransactionTemplate transactionTemplate;

    public SepayWebhookIngestService(
            BillingBankTransferIntentRepository bankTransferIntentRepository,
            BillingPlanRepository billingPlanRepository,
            BillingWebhookEventRepository billingWebhookEventRepository,
            ObjectMapper objectMapper,
            PaidPlanActivationService paidPlanActivationService,
            ApplicationEventPublisher applicationEventPublisher,
            TransactionTemplate transactionTemplate) {
        this.bankTransferIntentRepository = bankTransferIntentRepository;
        this.billingPlanRepository = billingPlanRepository;
        this.billingWebhookEventRepository = billingWebhookEventRepository;
        this.objectMapper = objectMapper;
        this.paidPlanActivationService = paidPlanActivationService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    public void ingest(String rawPayload) {
        String payload = rawPayload == null ? "" : rawPayload;
        JsonNode rootNode = readPayload(payload);
        SepayTransferPayload transferPayload = transferPayload(rootNode);
        Optional<BillingBankTransferIntentEntity> matchedIntent =
                findMatchingIntent(transferPayload);
        if (matchedIntent.isPresent()) {
            ScopedValue.where(TenantContext.TENANT, matchedIntent.get().getTenantId().toString())
                    .run(
                            () ->
                                    transactionTemplate.executeWithoutResult(
                                            ignoredTransactionStatus ->
                                                    ingestInTransaction(
                                                            payload, rootNode, transferPayload)));
            return;
        }
        transactionTemplate.executeWithoutResult(
                ignoredTransactionStatus ->
                        ingestInTransaction(payload, rootNode, transferPayload));
    }

    private void ingestInTransaction(
            String payload, JsonNode rootNode, SepayTransferPayload transferPayload) {
        String payloadSha256 = sha256Hex(payload);
        String providerEventId = transferPayload.transactionId().map(Object::toString).orElse(null);
        String dedupeKey = dedupeKey(providerEventId, payloadSha256);
        Optional<BillingWebhookEventEntity> existingWebhookEvent =
                billingWebhookEventRepository.findByDedupeKey(dedupeKey);
        if (existingWebhookEvent.isPresent()) {
            return;
        }

        BillingWebhookEventEntity webhookEvent =
                billingWebhookEventRepository.save(
                        new BillingWebhookEventEntity(
                                UUID.randomUUID(),
                                PROVIDER_SEPAY,
                                providerEventId,
                                dedupeKey,
                                eventName(transferPayload),
                                null,
                                providerEventId,
                                true,
                                PROCESSING_STATUS_RECEIVED,
                                Instant.now(),
                                payloadSha256,
                                redactedPayloadJson(rootNode)));
        processWebhookEvent(webhookEvent, transferPayload);
    }

    private void processWebhookEvent(
            BillingWebhookEventEntity webhookEvent, SepayTransferPayload transferPayload) {
        webhookEvent.markProcessing();
        try {
            if (!"in".equalsIgnoreCase(transferPayload.transferType().orElse(""))) {
                webhookEvent.markSkipped(Instant.now(), "sepay_transfer_not_inbound");
                return;
            }
            BillingBankTransferIntentEntity intent = resolveMatchingIntent(transferPayload);
            webhookEvent.setTenantIdForWebhookProcessing(intent.getTenantId());
            if (!"PENDING".equals(intent.getStatus())) {
                webhookEvent.markSkipped(Instant.now(), "bank_transfer_intent_not_pending");
                return;
            }
            Instant now = Instant.now();
            if (!intent.getExpiresAt().isAfter(now)) {
                webhookEvent.markSkipped(Instant.now(), "bank_transfer_intent_expired");
                return;
            }
            long transferAmountVnd =
                    transferPayload
                            .transferAmountVnd()
                            .orElseThrow(
                                    () ->
                                            new WebhookProcessingException(
                                                    "missing_transfer_amount"));
            if (intent.getAmountVnd() != transferAmountVnd) {
                webhookEvent.markFailed(Instant.now(), "bank_transfer_amount_mismatch");
                return;
            }

            String providerTransactionId =
                    transferPayload
                            .transactionId()
                            .map(Object::toString)
                            .orElseThrow(
                                    () ->
                                            new WebhookProcessingException(
                                                    "missing_sepay_transaction_id"));
            Instant paidAt = transferPayload.transactionDate().orElse(now);
            int updatedRows =
                    bankTransferIntentRepository.markPaidIfPending(
                            intent.getId(), providerTransactionId, paidAt, now);
            if (updatedRows == 0) {
                webhookEvent.markSkipped(Instant.now(), "bank_transfer_replay_ignored");
                return;
            }
            BillingPlanEntity billingPlan =
                    billingPlanRepository
                            .findById(intent.getPlanId())
                            .orElseThrow(
                                    () -> new WebhookProcessingException("billing_plan_not_found"));
            ScopedValue.where(TenantContext.TENANT, intent.getTenantId().toString())
                    .run(
                            () ->
                                    paidPlanActivationService.activate(
                                            new PaidPlanActivationService.PaidPlanActivationCommand(
                                                    intent.getTenantId(),
                                                    billingPlan,
                                                    PROVIDER_SEPAY,
                                                    providerTransactionId,
                                                    intent.getId().toString(),
                                                    webhookEvent.getProviderEventId(),
                                                    paidAt,
                                                    transferAmountVnd,
                                                    intent.getCurrency())));
            applicationEventPublisher.publishEvent(
                    new PlanUpgradePaymentCompleted(
                            intent.getTenantId(),
                            intent.getId(),
                            intent.getCode(),
                            billingPlan.getCode(),
                            PROVIDER_SEPAY,
                            providerTransactionId,
                            transferAmountVnd,
                            intent.getCurrency(),
                            paidAt));
            webhookEvent.markProcessed(Instant.now());
        } catch (PaidPlanActivationService.PlanActivationException
                | WebhookProcessingException processingException) {
            webhookEvent.markFailed(Instant.now(), processingException.getMessage());
        }
    }

    private BillingBankTransferIntentEntity resolveMatchingIntent(
            SepayTransferPayload transferPayload) {
        return findMatchingIntent(transferPayload)
                .orElseThrow(
                        () -> new WebhookProcessingException("bank_transfer_intent_not_found"));
    }

    private Optional<BillingBankTransferIntentEntity> findMatchingIntent(
            SepayTransferPayload transferPayload) {
        LinkedHashSet<String> candidateCodes =
                extractCandidateCodes(
                        transferPayload.referenceCode().orElse(null),
                        transferPayload.code().orElse(null),
                        transferPayload.content().orElse(null));
        for (String candidateCode : candidateCodes) {
            Optional<BillingBankTransferIntentEntity> intent =
                    bankTransferIntentRepository.findByCode(candidateCode);
            if (intent.isPresent()) {
                return intent;
            }
        }
        return Optional.empty();
    }

    private LinkedHashSet<String> extractCandidateCodes(
            String referenceCode, String code, String content) {
        LinkedHashSet<String> candidateCodes = new LinkedHashSet<>();
        addIfWholeCode(candidateCodes, referenceCode);
        addIfWholeCode(candidateCodes, code);
        if (content != null) {
            Matcher matcher = BANK_TRANSFER_CODE_PATTERN.matcher(content.toUpperCase(Locale.ROOT));
            while (matcher.find()) {
                candidateCodes.add(matcher.group());
            }
        }
        return candidateCodes;
    }

    private void addIfWholeCode(LinkedHashSet<String> candidateCodes, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        String normalizedValue = rawValue.trim().toUpperCase(Locale.ROOT);
        if (BANK_TRANSFER_CODE_PATTERN.matcher(normalizedValue).matches()) {
            candidateCodes.add(normalizedValue);
        }
    }

    private SepayTransferPayload transferPayload(JsonNode rootNode) {
        return new SepayTransferPayload(
                longValue(rootNode.path("id")),
                textValue(rootNode.path("code")),
                textValue(rootNode.path("content")),
                textValue(rootNode.path("transferType")),
                longValue(rootNode.path("transferAmount")),
                textValue(rootNode.path("referenceCode")),
                instantFromSepayDate(rootNode.path("transactionDate")));
    }

    private String eventName(SepayTransferPayload transferPayload) {
        return "sepay.transfer."
                + transferPayload.transferType().orElse("unknown").trim().toLowerCase(Locale.ROOT);
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

    private Optional<Instant> instantFromSepayDate(JsonNode jsonNode) {
        Optional<String> value = textValue(jsonNode);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    LocalDateTime.parse(value.get(), SEPAY_TRANSACTION_DATE_FORMAT)
                            .atZone(SEPAY_TRANSACTION_ZONE)
                            .toInstant());
        } catch (RuntimeException invalidTransactionDate) {
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
        return jsonNode;
    }

    private boolean isSensitiveField(String fieldName) {
        String normalizedFieldName = fieldName.toLowerCase(Locale.ROOT);
        return normalizedFieldName.equals("accountnumber")
                || normalizedFieldName.equals("subaccount")
                || normalizedFieldName.equals("content")
                || normalizedFieldName.equals("description");
    }

    private String dedupeKey(String providerEventId, String payloadSha256) {
        return providerEventId == null || providerEventId.isBlank()
                ? PROVIDER_SEPAY + ":payload_sha256:" + payloadSha256
                : PROVIDER_SEPAY + ":provider_event_id:" + providerEventId;
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

    private record SepayTransferPayload(
            Optional<Long> transactionId,
            Optional<String> code,
            Optional<String> content,
            Optional<String> transferType,
            Optional<Long> transferAmountVnd,
            Optional<String> referenceCode,
            Optional<Instant> transactionDate) {}

    private static class WebhookProcessingException extends RuntimeException {

        WebhookProcessingException(String message) {
            super(message);
        }
    }
}
