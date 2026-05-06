package com.zeromail.core.billing.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;
import com.zeromail.core.billing.persistence.BillingTopupIntentTenantLookup;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.tenant.TenantContext;

/**
 * Top-up intent lifecycle and SePay webhook handling.
 */
@Service
public class BillingTopupService {

    private static final Logger log = LoggerFactory.getLogger(BillingTopupService.class);
    private static final Pattern CROCKFORD_EIGHT_CHARACTER_CODE =
            Pattern.compile("[0-9A-HJKMNPQRSTVWXYZ]{8}");

    private final BillingTopupIntentRepository intentRepository;
    private final CreditLedgerEntryRepository entryRepository;
    private final TopupCodeGenerator topupCodeGenerator;
    private final BillingProperties billingProperties;
    private final TransactionTemplate transactionTemplate;

    public BillingTopupService(
            BillingTopupIntentRepository intentRepository,
            CreditLedgerEntryRepository entryRepository,
            TopupCodeGenerator topupCodeGenerator,
            BillingProperties billingProperties,
            TransactionTemplate transactionTemplate) {
        this.intentRepository = intentRepository;
        this.entryRepository = entryRepository;
        this.topupCodeGenerator = topupCodeGenerator;
        this.billingProperties = billingProperties;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BillingTopupIntentEntity createIntent(UUID tenantId, long amountVnd) {
        long vndPerCredit = billingProperties.vndPerCredit();
        if (amountVnd < vndPerCredit) {
            throw new IllegalArgumentException(
                    "Top-up amount must be at least " + vndPerCredit + " VND (one credit)");
        }

        int pendingCount = intentRepository.countByTenantIdAndStatus(tenantId, BillingTopupIntentStatus.PENDING);
        if (pendingCount >= billingProperties.maxPendingIntentsPerTenant()) {
            intentRepository.findFirstByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, BillingTopupIntentStatus.PENDING)
                    .ifPresent(oldestIntent -> {
                        oldestIntent.markExpired();
                        intentRepository.save(oldestIntent);
                    });
        }

        String code = topupCodeGenerator.generateUniqueCode(
                candidateCode -> intentRepository.findTenantLookupByCode(candidateCode).isEmpty(),
                3);

        Instant expiresAt = Instant.now().plus(billingProperties.intentExpiry());
        BillingTopupIntentEntity intent = new BillingTopupIntentEntity(
                UUID.randomUUID(), tenantId, code, amountVnd, BillingTopupIntentStatus.PENDING, expiresAt);
        intentRepository.save(intent);
        log.info("event=billing_topup_intent_created tenantId={} amountVnd={}", tenantId, amountVnd);
        return intent;
    }

    /**
     * Reads SePay's detected payment {@code code} first, with bank memo {@code content}
     * fallback. {@code referenceCode} is audit metadata only and is not used for lookup or
     * idempotency.
     */
    public void applyWebhook(
            long sepayTransactionId,
            String code,
            String referenceCode,
            String content,
            String transferType,
            long transferAmountVnd) {
        if (!"in".equalsIgnoreCase(transferType)) {
            log.warn("event=sepay_webhook_non_inbound_ignored");
            return;
        }

        Optional<String> maybeIntentCode = extractIntentCode(code, content);
        if (maybeIntentCode.isEmpty()) {
            log.warn("event=sepay_webhook_unknown_code");
            return;
        }

        Optional<BillingTopupIntentTenantLookup> maybeLookup =
                intentRepository.findTenantLookupByCode(maybeIntentCode.get());
        if (maybeLookup.isEmpty()) {
            log.warn("event=sepay_webhook_unknown_code");
            return;
        }

        BillingTopupIntentTenantLookup lookup = maybeLookup.get();
        if (lookup.status() != BillingTopupIntentStatus.PENDING) {
            log.warn("event=sepay_webhook_intent_not_pending");
            return;
        }
        if (lookup.expiresAt().toInstant().isBefore(Instant.now())) {
            log.warn("event=sepay_webhook_intent_expired");
            return;
        }
        if (lookup.amountVnd() != transferAmountVnd) {
            log.warn("event=sepay_webhook_amount_mismatch intentVnd={} actualVnd={}",
                    lookup.amountVnd(), transferAmountVnd);
            return;
        }

        ScopedValue.where(TenantContext.TENANT, lookup.tenantId().toString()).run(() ->
                transactionTemplate.executeWithoutResult(transactionStatus ->
                        applyTopupCreditTransactional(lookup.id(), sepayTransactionId, transferAmountVnd)));
    }

    private void applyTopupCreditTransactional(UUID intentId, long sepayTransactionId, long transferAmountVnd) {
        Optional<BillingTopupIntentEntity> maybeIntent = intentRepository.findById(intentId);
        if (maybeIntent.isEmpty()) {
            log.warn("event=sepay_webhook_intent_vanished_post_lookup");
            return;
        }

        BillingTopupIntentEntity intent = maybeIntent.get();
        if (intent.getStatus() != BillingTopupIntentStatus.PENDING) {
            log.info("event=sepay_topup_replay_ignored");
            return;
        }

        long credits = transferAmountVnd / billingProperties.vndPerCredit();
        long roundingLossVnd = transferAmountVnd - (credits * billingProperties.vndPerCredit());
        if (roundingLossVnd > 0) {
            log.info("event=sepay_topup_rounding_loss vndLost={}", roundingLossVnd);
        }
        if (credits <= 0) {
            log.warn("event=sepay_topup_below_min_credits transferAmountVnd={} vndPerCredit={}",
                    transferAmountVnd, billingProperties.vndPerCredit());
            return;
        }

        try {
            intent.markPaid(String.valueOf(sepayTransactionId));
            intentRepository.saveAndFlush(intent);
            CreditLedgerEntryEntity topupEntry = CreditLedgerEntryEntity.topup(
                    UUID.randomUUID(),
                    intent.getTenantId(),
                    Math.toIntExact(credits),
                    String.valueOf(sepayTransactionId));
            entryRepository.saveAndFlush(topupEntry);
            log.info("event=sepay_topup_credited tenantId={} credits={}", intent.getTenantId(), credits);
        } catch (DataIntegrityViolationException duplicateTopup) {
            log.info("event=sepay_topup_replay_ignored");
        }
    }

    private Optional<String> extractIntentCode(String code, String content) {
        if (code != null) {
            String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
            if (CROCKFORD_EIGHT_CHARACTER_CODE.matcher(normalizedCode).matches()) {
                return Optional.of(normalizedCode);
            }
        }
        if (content != null) {
            Matcher matcher = CROCKFORD_EIGHT_CHARACTER_CODE.matcher(content.toUpperCase(Locale.ROOT));
            if (matcher.find()) {
                return Optional.of(matcher.group());
            }
        }
        return Optional.empty();
    }
}
