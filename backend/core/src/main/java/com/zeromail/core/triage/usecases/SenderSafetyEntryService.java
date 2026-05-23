package com.zeromail.core.triage.usecases;

import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationRepository;
import com.zeromail.core.triage.persistence.TenantSenderOptInEntity;
import com.zeromail.core.triage.persistence.TenantSenderOptInRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SenderSafetyEntryService {

    private static final String MODE_OPTED_IN = "opted_in";
    private static final String MODE_PROTECTED = "protected";
    private static final String MODE_NOT_FOUND = "not_found";

    private final SenderEmailCanonicalizer senderEmailCanonicalizer;
    private final TenantProtectedSenderObservationRepository protectedSenderObservationRepository;
    private final TenantSenderOptInRepository senderOptInRepository;

    public SenderSafetyEntryService(
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            TenantProtectedSenderObservationRepository protectedSenderObservationRepository,
            TenantSenderOptInRepository senderOptInRepository) {
        this.senderEmailCanonicalizer = senderEmailCanonicalizer;
        this.protectedSenderObservationRepository = protectedSenderObservationRepository;
        this.senderOptInRepository = senderOptInRepository;
    }

    @Transactional(readOnly = true)
    public SenderSafetyEntry find(UUID tenantId, String senderEmail) {
        String canonicalSenderEmail = senderEmailCanonicalizer.canonicalize(senderEmail);
        Optional<TenantSenderOptInEntity> senderOptIn =
                senderOptInRepository.findByTenantId(tenantId).stream()
                        .filter(entity -> entity.getSenderEmail().equals(canonicalSenderEmail))
                        .findFirst();
        Optional<TenantProtectedSenderObservationEntity> protectedObservation =
                protectedSenderObservationRepository.findByTenantIdAndSenderEmail(
                        tenantId, canonicalSenderEmail);
        return senderOptIn
                .map(entity -> entry(MODE_OPTED_IN, entity.getCreatedAt(), canonicalSenderEmail))
                .orElseGet(
                        () ->
                                protectedObservation
                                        .map(
                                                entity ->
                                                        entry(
                                                                MODE_PROTECTED,
                                                                entity.getFirstObservedAt(),
                                                                canonicalSenderEmail))
                                        .orElseGet(
                                                () ->
                                                        entry(
                                                                MODE_NOT_FOUND,
                                                                null,
                                                                canonicalSenderEmail)));
    }

    @Transactional
    public SenderSafetyRemoval removeProtectedSender(UUID tenantId, String senderEmail) {
        String canonicalSenderEmail = senderEmailCanonicalizer.canonicalize(senderEmail);
        protectedSenderObservationRepository
                .findByTenantIdAndSenderEmail(tenantId, canonicalSenderEmail)
                .ifPresent(protectedSenderObservationRepository::delete);
        return new SenderSafetyRemoval(
                senderEmailCanonicalizer.redisCacheKeyComponent(canonicalSenderEmail), true);
    }

    private SenderSafetyEntry entry(String mode, Instant addedAt, String canonicalSenderEmail) {
        return new SenderSafetyEntry(
                senderEmailCanonicalizer.redisCacheKeyComponent(canonicalSenderEmail),
                mode,
                addedAt);
    }

    public record SenderSafetyEntry(String recipientEmailHash, String mode, Instant addedAt) {}

    public record SenderSafetyRemoval(String senderEmailHash, boolean removed) {}
}
