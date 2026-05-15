package com.zeromail.core.billing.usecases;

import com.zeromail.core.billing.domain.BillingTopupIntentStatus;
import com.zeromail.core.billing.domain.TopupCodeGenerator;
import com.zeromail.core.billing.event.BillingTopupCredited;
import com.zeromail.core.billing.persistence.BillingPackageEntity;
import com.zeromail.core.billing.persistence.BillingPackageRepository;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;
import com.zeromail.core.billing.persistence.BillingTopupIntentTenantLookup;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.BillingProperties.BillingPaymentAccountProperties;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Top-up intent lifecycle and SePay webhook handling. */
@Service
public class BillingTopupService {

    private static final Logger log = LoggerFactory.getLogger(BillingTopupService.class);
    private static final Pattern CROCKFORD_EIGHT_CHARACTER_CODE =
            Pattern.compile("[0-9A-HJKMNPQRSTVWXYZ]{8}");
    private static final Pattern PACKAGE_CODE_TOKEN = Pattern.compile("PKG_[A-Z0-9_]{2,32}");

    private final BillingPackageRepository packageRepository;
    private final BillingTopupIntentRepository intentRepository;
    private final CreditLedgerEntryRepository entryRepository;
    private final TopupCodeGenerator topupCodeGenerator = new TopupCodeGenerator();
    private final ZeroMailCoreProperties.BillingProperties billingProperties;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public BillingTopupService(
            BillingPackageRepository packageRepository,
            BillingTopupIntentRepository intentRepository,
            CreditLedgerEntryRepository entryRepository,
            ZeroMailCoreProperties properties,
            TransactionTemplate transactionTemplate,
            ApplicationEventPublisher eventPublisher) {
        this.packageRepository = packageRepository;
        this.intentRepository = intentRepository;
        this.entryRepository = entryRepository;
        this.billingProperties = properties.billing();
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<BillingPackageEntity> listActivePackages() {
        return packageRepository.findByActiveTrueOrderByDisplayOrderAscCodeAsc();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public BillingTopupIntentEntity createIntent(UUID tenantId, String packageCode) {
        String normalizedPackageCode = normalizeRequiredPackageCode(packageCode);
        BillingPackageEntity billingPackage =
                packageRepository
                        .findByCodeAndActiveTrue(normalizedPackageCode)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unknown or inactive billing package: "
                                                        + normalizedPackageCode));
        Instant now = Instant.now();
        Optional<BillingTopupIntentEntity> reusableIntent =
                intentRepository
                        .findFirstByTenantIdAndPackageCodeSnapshotAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                tenantId,
                                billingPackage.getCode(),
                                BillingTopupIntentStatus.PENDING,
                                now);
        if (reusableIntent.isPresent()) {
            BillingTopupIntentEntity existingIntent = reusableIntent.get();
            log.info(
                    "event=billing_topup_intent_reused tenantId={} packageCode={} amountVnd={} credits={}",
                    tenantId,
                    existingIntent.getPackageCodeSnapshot(),
                    existingIntent.getAmountVnd(),
                    existingIntent.getCreditAmountSnapshot());
            return existingIntent;
        }

        int pendingCount =
                intentRepository.countByTenantIdAndStatus(
                        tenantId, BillingTopupIntentStatus.PENDING);
        if (pendingCount >= billingProperties.maxPendingIntentsPerTenant()) {
            intentRepository
                    .findFirstByTenantIdAndStatusOrderByCreatedAtAsc(
                            tenantId, BillingTopupIntentStatus.PENDING)
                    .ifPresent(
                            oldestIntent -> {
                                oldestIntent.markExpired();
                                intentRepository.save(oldestIntent);
                            });
        }

        String orderCode =
                topupCodeGenerator.generateUniqueCode(
                        candidateCode ->
                                intentRepository.findTenantLookupByCode(candidateCode).isEmpty(),
                        3);

        Instant expiresAt = now.plus(billingProperties.intentExpiry());
        BillingPaymentAccountProperties paymentAccount = billingProperties.paymentAccount();
        String transferContent = buildTransferContent(orderCode, billingPackage.getCode());
        BillingTopupIntentEntity intent =
                new BillingTopupIntentEntity(
                        UUID.randomUUID(),
                        tenantId,
                        orderCode,
                        billingPackage.getPriceVnd(),
                        billingPackage.getId(),
                        billingPackage.getCode(),
                        billingPackage.getName(),
                        billingPackage.getCreditAmount(),
                        BillingTopupIntentStatus.PENDING,
                        expiresAt,
                        paymentAccount.bankCode(),
                        paymentAccount.bankName(),
                        paymentAccount.accountNumber(),
                        paymentAccount.accountName(),
                        transferContent,
                        paymentAccount.qrPayload());
        intentRepository.save(intent);
        log.info(
                "event=billing_topup_intent_created tenantId={} packageCode={} amountVnd={} credits={}",
                tenantId,
                billingPackage.getCode(),
                billingPackage.getPriceVnd(),
                billingPackage.getCreditAmount());
        return intent;
    }

    /**
     * Walks every plausible top-up code that may appear in the SePay payload (referenceCode, the
     * detected code field, and any 8-character Crockford token in the bank memo content), in that
     * priority order, deduplicated. The first candidate that resolves to a {@code PENDING},
     * unexpired intent with the exact transferred amount is credited. If no candidate is fully
     * valid, the most specific known-intent failure is logged (status mismatch, expiry, or amount
     * mismatch); only if no candidate resolves to any known intent do we fall through as {@code
     * unknown_code}. Bank memo text and account numbers are never logged.
     */
    public void applyWebhook(
            long sepayTransactionId,
            String code,
            String referenceCode,
            String packageCode,
            String content,
            String transferType,
            long transferAmountVnd) {
        if (!"in".equalsIgnoreCase(transferType)) {
            log.warn("event=sepay_webhook_non_inbound_ignored tenantId=unresolved");
            return;
        }

        LinkedHashSet<String> candidateCodes =
                extractCandidateIntentCodes(referenceCode, code, content);
        if (candidateCodes.isEmpty()) {
            log.warn("event=sepay_webhook_unknown_code tenantId=unresolved");
            return;
        }
        LinkedHashSet<String> candidatePackageCodes =
                extractCandidatePackageCodes(packageCode, referenceCode, code, content);

        BillingTopupIntentTenantLookup matchedLookup = null;
        BillingTopupIntentTenantLookup firstKnownLookup = null;
        for (String candidateCode : candidateCodes) {
            Optional<BillingTopupIntentTenantLookup> maybeLookup =
                    intentRepository.findTenantLookupByCode(candidateCode);
            if (maybeLookup.isEmpty()) {
                continue;
            }
            BillingTopupIntentTenantLookup lookup = maybeLookup.get();
            if (firstKnownLookup == null) {
                firstKnownLookup = lookup;
            }
            if (lookup.status() != BillingTopupIntentStatus.PENDING) {
                continue;
            }
            if (lookup.expiresAt().toInstant().isBefore(Instant.now())) {
                continue;
            }
            if (lookup.amountVnd() != transferAmountVnd) {
                continue;
            }
            if (!packageCodeMatches(candidatePackageCodes, lookup.packageCodeSnapshot())) {
                continue;
            }
            matchedLookup = lookup;
            break;
        }

        if (matchedLookup == null) {
            if (firstKnownLookup == null) {
                log.warn("event=sepay_webhook_unknown_code tenantId=unresolved");
                return;
            }
            if (firstKnownLookup.status() != BillingTopupIntentStatus.PENDING) {
                log.warn(
                        "event=sepay_webhook_intent_not_pending tenantId={}",
                        firstKnownLookup.tenantId());
                return;
            }
            if (firstKnownLookup.expiresAt().toInstant().isBefore(Instant.now())) {
                log.warn(
                        "event=sepay_webhook_intent_expired tenantId={}",
                        firstKnownLookup.tenantId());
                return;
            }
            if (firstKnownLookup.amountVnd() != transferAmountVnd) {
                log.warn(
                        "event=sepay_webhook_amount_mismatch tenantId={} intentVnd={} actualVnd={}",
                        firstKnownLookup.tenantId(),
                        firstKnownLookup.amountVnd(),
                        transferAmountVnd);
                return;
            }
            log.warn(
                    "event=sepay_webhook_package_mismatch tenantId={} packageCode={}",
                    firstKnownLookup.tenantId(),
                    firstKnownLookup.packageCodeSnapshot());
            return;
        }

        BillingTopupIntentTenantLookup lookup = matchedLookup;
        ScopedValue.where(TenantContext.TENANT, lookup.tenantId().toString())
                .run(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        transactionStatus ->
                                                applyTopupCreditTransactional(
                                                        lookup.id(),
                                                        sepayTransactionId,
                                                        lookup.creditAmountSnapshot())));
    }

    private void applyTopupCreditTransactional(
            UUID intentId, long sepayTransactionId, int creditAmountSnapshot) {
        Optional<BillingTopupIntentEntity> maybeIntent = intentRepository.findById(intentId);
        if (maybeIntent.isEmpty()) {
            log.warn("event=sepay_webhook_intent_vanished_post_lookup tenantId=unresolved");
            return;
        }

        BillingTopupIntentEntity intent = maybeIntent.get();
        if (intent.getStatus() != BillingTopupIntentStatus.PENDING) {
            log.info("event=sepay_topup_replay_ignored tenantId={}", intent.getTenantId());
            return;
        }

        int credits = resolveTopupCredits(intent, creditAmountSnapshot);
        if (credits <= 0) {
            log.warn(
                    "event=sepay_topup_missing_credit_snapshot tenantId={} intentId={}",
                    intent.getTenantId(),
                    intent.getId());
            return;
        }

        String sepayTransactionIdString = String.valueOf(sepayTransactionId);
        int rowsUpdated = intentRepository.markPaidIfPending(intentId, sepayTransactionIdString);
        if (rowsUpdated == 0) {
            // Concurrent webhook delivery: another thread already moved this intent to PAID. Per
            // the
            // SePay endpoint contract, duplicate delivery must respond 200 to stop retries — return
            // here so the controller produces a success body. The ledger row uniqueness on
            // (ref_type, ref_id) is enforced by the winning thread; this loser must not insert
            // again.
            log.info("event=sepay_topup_replay_ignored tenantId={}", intent.getTenantId());
            return;
        }

        try {
            CreditLedgerEntryEntity topupEntry =
                    CreditLedgerEntryEntity.topup(
                            UUID.randomUUID(),
                            intent.getTenantId(),
                            credits,
                            sepayTransactionIdString);
            entryRepository.saveAndFlush(topupEntry);
            Instant creditedAt = Instant.now();
            eventPublisher.publishEvent(
                    new BillingTopupCredited(
                            intent.getTenantId(),
                            intent.getId(),
                            intent.getCode(),
                            intent.getPackageCodeSnapshot(),
                            intent.getPackageNameSnapshot(),
                            intent.getAmountVnd(),
                            credits,
                            sepayTransactionIdString,
                            creditedAt));
            log.info(
                    "event=sepay_topup_credited tenantId={} credits={}",
                    intent.getTenantId(),
                    credits);
        } catch (DataIntegrityViolationException duplicateTopup) {
            // Defensive: the conditional UPDATE already serializes status transitions, but the
            // unique
            // index on (ref_type, ref_id) still guards against any historical race window. Treat as
            // replay so SePay receives 200.
            log.info("event=sepay_topup_replay_ignored tenantId={}", intent.getTenantId());
        }
    }

    private int resolveTopupCredits(
            BillingTopupIntentEntity intent, int projectedCreditAmountSnapshot) {
        if (intent.getCreditAmountSnapshot() != null) {
            return intent.getCreditAmountSnapshot();
        }
        if (projectedCreditAmountSnapshot > 0) {
            return projectedCreditAmountSnapshot;
        }
        long vndPerCredit = billingProperties.vndPerCredit();
        if (vndPerCredit <= 0 || intent.getAmountVnd() % vndPerCredit != 0) {
            return 0;
        }
        return Math.toIntExact(intent.getAmountVnd() / vndPerCredit);
    }

    /**
     * Collects every plausible 8-character Crockford-shaped top-up code from the three SePay
     * payload fields. Priority order matches typical reliability: {@code referenceCode}
     * (SePay-detected reference) first, then the {@code code} field if it is itself a single
     * 8-character token, then every 8-character match scanned out of the bank memo {@code content}.
     * Duplicates are dropped while preserving insertion order. The result is normalized to
     * upper-case so case-insensitive lookups hit the unique {@code billing_topup_intent.code}
     * index.
     */
    private LinkedHashSet<String> extractCandidateIntentCodes(
            String referenceCode, String code, String content) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addIfMatchesWholeToken(candidates, referenceCode);
        addIfMatchesWholeToken(candidates, code);
        if (content != null) {
            Matcher matcher =
                    CROCKFORD_EIGHT_CHARACTER_CODE.matcher(content.toUpperCase(Locale.ROOT));
            while (matcher.find()) {
                candidates.add(matcher.group());
            }
        }
        return candidates;
    }

    private static void addIfMatchesWholeToken(LinkedHashSet<String> candidates, String rawValue) {
        if (rawValue == null) {
            return;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (CROCKFORD_EIGHT_CHARACTER_CODE.matcher(normalized).matches()) {
            candidates.add(normalized);
        }
    }

    private LinkedHashSet<String> extractCandidatePackageCodes(
            String packageCode, String referenceCode, String code, String content) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addPackageCodeIfPresent(candidates, packageCode);
        scanPackageCodes(candidates, referenceCode);
        scanPackageCodes(candidates, code);
        scanPackageCodes(candidates, content);
        return candidates;
    }

    private static void addPackageCodeIfPresent(LinkedHashSet<String> candidates, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (PACKAGE_CODE_TOKEN.matcher(normalized).matches()) {
            candidates.add(normalized);
        }
    }

    private static void scanPackageCodes(LinkedHashSet<String> candidates, String rawValue) {
        if (rawValue == null) {
            return;
        }
        Matcher matcher = PACKAGE_CODE_TOKEN.matcher(rawValue.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            candidates.add(matcher.group());
        }
    }

    private static boolean packageCodeMatches(
            LinkedHashSet<String> candidatePackageCodes, String packageCodeSnapshot) {
        if (candidatePackageCodes.isEmpty() || packageCodeSnapshot == null) {
            return true;
        }
        return candidatePackageCodes.contains(packageCodeSnapshot.toUpperCase(Locale.ROOT));
    }

    private static String normalizeRequiredPackageCode(String packageCode) {
        if (packageCode == null || packageCode.isBlank()) {
            throw new IllegalArgumentException("Billing package code is required");
        }
        return packageCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String buildTransferContent(String orderCode, String packageCode) {
        return "ZM " + orderCode + " " + packageCode;
    }
}
