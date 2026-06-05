package com.zeromail.core.triage.usecases;

import com.zeromail.core.triage.exception.SenderSafetyNetException;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationRepository;
import com.zeromail.core.triage.persistence.TenantSenderOptInRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether automated Gmail writes (label, archive, save-draft, and outbound send) may run
 * for a given sender.
 *
 * <p>A sender is <b>protected</b> only when it matches an entry the tenant has explicitly added to
 * their protected-sender list in settings ({@code tenant_protected_sender_observation} rows with
 * {@code created_by_user = true}). There is no automatic derivation from Gmail send history — the
 * protected set is entirely user-managed via {@link #optInSender}/{@link #listProtectedSenders}/
 * {@link #deleteByIdAndTenant}. A protected verdict downgrades outbound sends to a Gmail draft.
 *
 * <p>A sender on the {@code tenant_sender_opt_in} exemption list is never protected.
 */
@Component
public class SenderSafetyNetService {

    private static final Logger log = LoggerFactory.getLogger(SenderSafetyNetService.class);

    private final SenderEmailCanonicalizer senderEmailCanonicalizer;
    private final SafetyNetMatcher safetyNetMatcher;
    private final TenantSenderOptInRepository tenantSenderOptInRepository;
    private final TenantProtectedSenderObservationRepository
            tenantProtectedSenderObservationRepository;
    private final Clock clock;

    @Autowired
    public SenderSafetyNetService(
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            SafetyNetMatcher safetyNetMatcher,
            TenantSenderOptInRepository tenantSenderOptInRepository,
            TenantProtectedSenderObservationRepository tenantProtectedSenderObservationRepository) {
        this(
                senderEmailCanonicalizer,
                safetyNetMatcher,
                tenantSenderOptInRepository,
                tenantProtectedSenderObservationRepository,
                Clock.systemUTC());
    }

    SenderSafetyNetService(
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            SafetyNetMatcher safetyNetMatcher,
            TenantSenderOptInRepository tenantSenderOptInRepository,
            TenantProtectedSenderObservationRepository tenantProtectedSenderObservationRepository,
            Clock clock) {
        this.senderEmailCanonicalizer = senderEmailCanonicalizer;
        this.safetyNetMatcher = safetyNetMatcher;
        this.tenantSenderOptInRepository = tenantSenderOptInRepository;
        this.tenantProtectedSenderObservationRepository =
                tenantProtectedSenderObservationRepository;
        this.clock = clock;
    }

    public boolean isProtected(UUID tenantId, String senderEmail) {
        return matchedProtectedPattern(tenantId, senderEmail).isPresent();
    }

    public Optional<String> matchedProtectedPattern(UUID tenantId, String senderEmail) {
        String canonicalSenderEmail = senderEmailCanonicalizer.canonicalize(senderEmail);
        String senderEmailHash =
                senderEmailCanonicalizer.redisCacheKeyComponent(canonicalSenderEmail);
        if (tenantSenderOptInRepository.existsByTenantIdAndSenderEmail(
                tenantId, canonicalSenderEmail)) {
            logChecked(tenantId, senderEmailHash, false, "optin");
            return Optional.empty();
        }

        Optional<String> storedProtectedPattern =
                storedProtectedPattern(tenantId, canonicalSenderEmail);
        logChecked(tenantId, senderEmailHash, storedProtectedPattern.isPresent(), "user_list");
        return storedProtectedPattern;
    }

    @Transactional
    public ProtectedSenderListItem optInSender(UUID tenantId, String senderPattern) {
        SenderEmailCanonicalizer.CanonicalizedPattern canonicalizedPattern;
        try {
            canonicalizedPattern = senderEmailCanonicalizer.canonicalizePattern(senderPattern);
        } catch (IllegalArgumentException invalidPattern) {
            throw SenderSafetyNetException.patternInvalid();
        }
        Instant observedAt = clock.instant();
        tenantProtectedSenderObservationRepository.upsertUserPattern(
                UUID.randomUUID(),
                tenantId,
                canonicalizedPattern.value(),
                canonicalizedPattern.kind().id(),
                observedAt);
        if (canonicalizedPattern.kind()
                == TenantProtectedSenderObservationEntity.PatternKind.EMAIL) {
            String senderEmailHash =
                    senderEmailCanonicalizer.redisCacheKeyComponent(canonicalizedPattern.value());
            log.info(
                    "event=triage_sender_opt_in tenantId={} senderEmailHash={}",
                    tenantId,
                    senderEmailHash);
        } else {
            log.info(
                    "event=triage_sender_safety_net_domain_added tenantId={} patternKind={}",
                    tenantId,
                    canonicalizedPattern.kind().id());
        }
        return tenantProtectedSenderObservationRepository
                .findByTenantIdAndSenderEmail(tenantId, canonicalizedPattern.value())
                .map(SenderSafetyNetService::toProtectedSenderListItem)
                .orElseThrow(SenderSafetyNetException::notFound);
    }

    @Transactional
    public void deleteByIdAndTenant(UUID tenantId, UUID protectedSenderId) {
        TenantProtectedSenderObservationEntity protectedSenderObservation =
                tenantProtectedSenderObservationRepository
                        .findByIdAndTenantId(protectedSenderId, tenantId)
                        .orElseThrow(SenderSafetyNetException::notFound);
        if (!protectedSenderObservation.isCreatedByUser()) {
            throw SenderSafetyNetException.observationNotDeletable();
        }
        tenantProtectedSenderObservationRepository.delete(protectedSenderObservation);
    }

    @Transactional(readOnly = true)
    public List<ProtectedSenderListItem> listProtectedSenders(UUID tenantId) {
        return userManagedObservations(tenantId).stream()
                .sorted(
                        java.util.Comparator.comparing(
                                        TenantProtectedSenderObservationEntity::getLastObservedAt)
                                .reversed())
                .map(SenderSafetyNetService::toProtectedSenderListItem)
                .toList();
    }

    private Optional<String> storedProtectedPattern(UUID tenantId, String canonicalSenderEmail) {
        return safetyNetMatcher.findMatch(canonicalSenderEmail, userManagedObservations(tenantId));
    }

    /**
     * Only user-added entries protect a sender. Any legacy auto-observed rows ({@code
     * created_by_user = false}) are ignored so the protected set is exactly what the tenant
     * configured.
     */
    private List<TenantProtectedSenderObservationEntity> userManagedObservations(UUID tenantId) {
        return tenantProtectedSenderObservationRepository.findByTenantId(tenantId).stream()
                .filter(TenantProtectedSenderObservationEntity::isCreatedByUser)
                .toList();
    }

    private void logChecked(
            UUID tenantId, String senderEmailHash, boolean protectedFlag, String source) {
        log.info(
                "event=triage_sender_safety_net_checked tenantId={} senderEmailHash={} protected={} source={}",
                tenantId,
                senderEmailHash,
                protectedFlag,
                source);
    }

    private static ProtectedSenderListItem toProtectedSenderListItem(
            TenantProtectedSenderObservationEntity protectedSenderObservation) {
        return new ProtectedSenderListItem(
                protectedSenderObservation.getId(),
                protectedSenderObservation.getSenderEmail(),
                protectedSenderObservation.getPatternKindId(),
                protectedSenderObservation.isCreatedByUser(),
                protectedSenderObservation.getCreatedAt());
    }
}
