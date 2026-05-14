package com.zeromail.core.notification.usecases;

import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.notification.domain.DigestDeliveryStatus;
import com.zeromail.core.notification.persistence.DigestDeliveryEntity;
import com.zeromail.core.notification.persistence.DigestDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigestDeliveryService {

    private static final ChannelType DEFAULT_CHANNEL = ChannelType.EMAIL;

    private final DigestDeliveryRepository digestDeliveryRepository;

    public DigestDeliveryService(DigestDeliveryRepository digestDeliveryRepository) {
        this.digestDeliveryRepository =
                Objects.requireNonNull(
                        digestDeliveryRepository, "digestDeliveryRepository must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DigestClaimRecord claimPending(UUID tenantId, LocalDate digestDayLocal) {
        DigestDeliveryEntity digestDelivery =
                new DigestDeliveryEntity(
                        UUID.randomUUID(),
                        tenantId,
                        digestDayLocal,
                        DigestDeliveryStatus.PENDING,
                        DEFAULT_CHANNEL,
                        1);
        try {
            DigestDeliveryEntity savedDelivery =
                    digestDeliveryRepository.saveAndFlush(digestDelivery);
            return new DigestClaimRecord(
                    savedDelivery.getId(),
                    savedDelivery.getTenantId(),
                    savedDelivery.getDigestDayLocal(),
                    savedDelivery.getAttemptCount(),
                    savedDelivery.getChannel());
        } catch (DataIntegrityViolationException duplicateDigestDelivery) {
            throw new DigestAlreadyClaimedException(
                    tenantId, digestDayLocal, duplicateDigestDelivery);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID deliveryId, ChannelType channel, String externalRef) {
        DigestDeliveryEntity digestDelivery =
                digestDeliveryRepository.findById(deliveryId).orElseThrow();
        digestDelivery.setStatus(DigestDeliveryStatus.SENT);
        digestDelivery.setChannel(channel);
        digestDelivery.setDispatchedAt(Instant.now());
        digestDelivery.setExternalRef(externalRef);
        digestDelivery.setFailureReason(null);
        digestDeliveryRepository.save(digestDelivery);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID deliveryId, String failureReason) {
        DigestDeliveryEntity digestDelivery =
                digestDeliveryRepository.findById(deliveryId).orElseThrow();
        digestDelivery.setStatus(DigestDeliveryStatus.FAILED);
        digestDelivery.setFailureReason(failureReason);
        digestDeliveryRepository.save(digestDelivery);
    }

    @Transactional(readOnly = true)
    public List<DigestDeliveryEntity> findStuckPending(Duration gracePeriod, int batchLimit) {
        Instant cutoff = Instant.now().minus(gracePeriod);
        return digestDeliveryRepository.findStuckByStatusBefore(
                DigestDeliveryStatus.PENDING, cutoff, PageRequest.of(0, batchLimit));
    }

    @Transactional
    public void deleteForTenant(UUID tenantId) {
        digestDeliveryRepository.deleteByTenantId(tenantId);
    }
}
