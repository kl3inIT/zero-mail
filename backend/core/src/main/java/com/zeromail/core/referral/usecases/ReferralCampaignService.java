package com.zeromail.core.referral.usecases;

import com.zeromail.core.referral.domain.ReferralCampaignStatus;
import com.zeromail.core.referral.domain.ReferralCodeStatus;
import com.zeromail.core.referral.exception.ReferralCampaignActiveConflictException;
import com.zeromail.core.referral.exception.ReferralCampaignBannerInvalidException;
import com.zeromail.core.referral.exception.ReferralCampaignInactiveException;
import com.zeromail.core.referral.exception.ReferralCampaignNotFoundException;
import com.zeromail.core.referral.exception.ReferralCodeNotFoundException;
import com.zeromail.core.referral.exception.ReferralSelfConversionException;
import com.zeromail.core.referral.persistence.ReferralCampaignBannerStorage;
import com.zeromail.core.referral.persistence.ReferralCampaignBannerStoredObject;
import com.zeromail.core.referral.persistence.ReferralCampaignEntity;
import com.zeromail.core.referral.persistence.ReferralCampaignRepository;
import com.zeromail.core.referral.persistence.ReferralCodeEntity;
import com.zeromail.core.referral.persistence.ReferralCodeRepository;
import com.zeromail.core.referral.persistence.ReferralConversionEntity;
import com.zeromail.core.referral.persistence.ReferralConversionRepository;
import com.zeromail.core.referral.persistence.lowlevel.ReferralTenantEligibilityRepository;
import com.zeromail.core.referral.projection.ReferralCampaignBannerImage;
import com.zeromail.core.referral.projection.ReferralCampaignBannerReference;
import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import com.zeromail.core.referral.projection.ReferralTenantCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralCampaignService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 20;
    private static final int MAX_BANNER_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_BANNER_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final ReferralCampaignRepository referralCampaignRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralConversionRepository referralConversionRepository;
    private final ReferralTenantEligibilityRepository referralTenantEligibilityRepository;
    private final ReferralCampaignBannerStorage referralCampaignBannerStorage;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ReferralCampaignService(
            ReferralCampaignRepository referralCampaignRepository,
            ReferralCodeRepository referralCodeRepository,
            ReferralConversionRepository referralConversionRepository,
            ReferralTenantEligibilityRepository referralTenantEligibilityRepository,
            ReferralCampaignBannerStorage referralCampaignBannerStorage,
            Clock clock) {
        this.referralCampaignRepository =
                Objects.requireNonNull(
                        referralCampaignRepository, "referralCampaignRepository must not be null");
        this.referralCodeRepository =
                Objects.requireNonNull(
                        referralCodeRepository, "referralCodeRepository must not be null");
        this.referralConversionRepository =
                Objects.requireNonNull(
                        referralConversionRepository,
                        "referralConversionRepository must not be null");
        this.referralTenantEligibilityRepository =
                Objects.requireNonNull(
                        referralTenantEligibilityRepository,
                        "referralTenantEligibilityRepository must not be null");
        this.referralCampaignBannerStorage =
                Objects.requireNonNull(
                        referralCampaignBannerStorage,
                        "referralCampaignBannerStorage must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public ReferralCampaignSnapshot createCampaign(ReferralCampaignCreateCommand command) {
        if (command.status() == ReferralCampaignStatus.ACTIVE) {
            ensureNoOtherActiveCampaign(null);
        }
        Instant now = clock.instant();
        ReferralCampaignEntity referralCampaign =
                new ReferralCampaignEntity(UUID.randomUUID(), command, now);
        return referralCampaignRepository.save(referralCampaign).toSnapshot();
    }

    @Transactional
    public ReferralCampaignSnapshot updateCampaign(
            UUID campaignId, ReferralCampaignUpdateCommand command) {
        ReferralCampaignEntity referralCampaign = campaignOrThrow(campaignId);
        referralCampaign.update(command, clock.instant());
        return referralCampaignRepository.save(referralCampaign).toSnapshot();
    }

    @Transactional
    public ReferralCampaignSnapshot updateCampaignStatus(
            UUID campaignId, ReferralCampaignStatus status) {
        ReferralCampaignEntity referralCampaign = campaignOrThrow(campaignId);
        if (status == ReferralCampaignStatus.ACTIVE) {
            ensureNoOtherActiveCampaign(campaignId);
        }
        referralCampaign.setStatus(status, clock.instant());
        return referralCampaignRepository.save(referralCampaign).toSnapshot();
    }

    @Transactional
    public int endExpiredActiveCampaigns() {
        Instant now = clock.instant();
        List<ReferralCampaignEntity> expiredActiveCampaigns =
                referralCampaignRepository.findAllByStatusAndEndsAtLessThanEqualOrderByEndsAtAsc(
                        ReferralCampaignStatus.ACTIVE, now);
        if (expiredActiveCampaigns.isEmpty()) {
            return 0;
        }
        for (ReferralCampaignEntity expiredActiveCampaign : expiredActiveCampaigns) {
            expiredActiveCampaign.setStatus(ReferralCampaignStatus.ENDED, now);
        }
        referralCampaignRepository.saveAll(expiredActiveCampaigns);
        return expiredActiveCampaigns.size();
    }

    @Transactional
    public boolean endCampaignIfExpired(UUID campaignId) {
        ReferralCampaignEntity referralCampaign = campaignOrThrow(campaignId);
        Instant now = clock.instant();
        if (referralCampaign.getStatus() != ReferralCampaignStatus.ACTIVE
                || referralCampaign.getEndsAt().isAfter(now)) {
            return false;
        }
        referralCampaign.setStatus(ReferralCampaignStatus.ENDED, now);
        referralCampaignRepository.save(referralCampaign);
        return true;
    }

    @Transactional
    public ReferralCampaignSnapshot updateCampaignBanner(
            UUID campaignId, byte[] imageBytes, String contentType) {
        byte[] validatedImageBytes = validateBannerImageBytes(imageBytes);
        String normalizedContentType = validateBannerContentType(contentType);
        ReferralCampaignEntity referralCampaign = campaignOrThrow(campaignId);
        String previousObjectKey =
                referralCampaign
                        .bannerImageReference()
                        .map(ReferralCampaignBannerReference::objectKey)
                        .orElse(null);
        ReferralCampaignBannerStoredObject storedObject =
                referralCampaignBannerStorage.store(
                        campaignId, validatedImageBytes, normalizedContentType);
        try {
            referralCampaign.setBannerImageReference(
                    storedObject.objectKey(),
                    storedObject.contentType(),
                    storedObject.sizeBytes(),
                    clock.instant());
            ReferralCampaignSnapshot updatedCampaign =
                    referralCampaignRepository.save(referralCampaign).toSnapshot();
            referralCampaignBannerStorage.deleteIfPresent(previousObjectKey);
            return updatedCampaign;
        } catch (RuntimeException storageMetadataFailure) {
            referralCampaignBannerStorage.deleteIfPresent(storedObject.objectKey());
            throw storageMetadataFailure;
        }
    }

    @Transactional(readOnly = true)
    public Optional<ReferralCampaignBannerImage> campaignBanner(UUID campaignId) {
        return campaignOrThrow(campaignId)
                .bannerImageReference()
                .flatMap(
                        referralCampaignBannerReference ->
                                referralCampaignBannerStorage
                                        .read(referralCampaignBannerReference.objectKey())
                                        .map(
                                                imageBytes ->
                                                        new ReferralCampaignBannerImage(
                                                                imageBytes,
                                                                referralCampaignBannerReference
                                                                        .contentType(),
                                                                referralCampaignBannerReference
                                                                        .sizeBytes())));
    }

    @Transactional(readOnly = true)
    public List<ReferralCampaignSnapshot> listCampaigns() {
        return referralCampaignRepository.findAllByOrderByStartsAtDescCreatedAtDesc().stream()
                .map(ReferralCampaignEntity::toSnapshot)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReferralCampaignSnapshot campaign(UUID campaignId) {
        return campaignOrThrow(campaignId).toSnapshot();
    }

    @Transactional(readOnly = true)
    public Optional<ReferralCampaignSnapshot> activeCampaign() {
        Instant now = clock.instant();
        return referralCampaignRepository
                .findFirstByStatusAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtDesc(
                        ReferralCampaignStatus.ACTIVE, now, now)
                .map(ReferralCampaignEntity::toSnapshot);
    }

    @Transactional(readOnly = true)
    public Optional<ReferralCampaignSnapshot> referralPageCampaign() {
        Instant now = clock.instant();
        return referralCampaignRepository
                .findFirstByStatusAndStartsAtLessThanEqualAndEndsAtAfterOrderByStartsAtDesc(
                        ReferralCampaignStatus.ACTIVE, now, now)
                .or(
                        () ->
                                referralCampaignRepository
                                        .findFirstByStatusInAndEndsAtLessThanEqualOrderByEndsAtDescCreatedAtDesc(
                                                Set.of(
                                                        ReferralCampaignStatus.ACTIVE,
                                                        ReferralCampaignStatus.ENDED),
                                                now))
                .map(ReferralCampaignEntity::toSnapshot);
    }

    @Transactional
    public ReferralTenantCode getOrCreateTenantCode(UUID campaignId, UUID ownerTenantId) {
        return referralCodeRepository
                .findByCampaignIdAndOwnerTenantId(campaignId, ownerTenantId)
                .map(ReferralCodeEntity::toTenantCode)
                .orElseGet(() -> createTenantCode(campaignId, ownerTenantId));
    }

    @Transactional
    public Optional<ReferralTenantCode> getOrCreateActiveTenantCode(UUID ownerTenantId) {
        return activeCampaign()
                .map(
                        referralCampaignSnapshot ->
                                getOrCreateTenantCode(
                                        referralCampaignSnapshot.campaignId(), ownerTenantId));
    }

    @Transactional
    public void qualifyConversion(
            String code, UUID referredTenantId, Instant attributedAt, Instant qualifiedAt) {
        Objects.requireNonNull(attributedAt, "attributedAt must not be null");
        Objects.requireNonNull(qualifiedAt, "qualifiedAt must not be null");
        ReferralCodeEntity referralCode =
                referralCodeRepository
                        .findByCode(code)
                        .orElseThrow(
                                () -> new ReferralCodeNotFoundException("Referral code not found"));
        ReferralCampaignEntity referralCampaign = campaignOrThrow(referralCode.getCampaignId());
        if (referralCode.getStatus() != ReferralCodeStatus.ACTIVE) {
            throw new ReferralCampaignInactiveException("Referral code is not active");
        }
        if (referralCampaign.getStatus() != ReferralCampaignStatus.ACTIVE
                || qualifiedAt.isBefore(referralCampaign.getStartsAt())
                || !qualifiedAt.isBefore(referralCampaign.getEndsAt())) {
            throw new ReferralCampaignInactiveException("Referral campaign is not active");
        }
        if (referralCode.getOwnerTenantId().equals(referredTenantId)) {
            throw new ReferralSelfConversionException("Tenant cannot refer itself");
        }
        if (attributedAt.isAfter(qualifiedAt)
                || !referralTenantEligibilityRepository.wasTenantCreatedAtOrAfter(
                        referredTenantId, attributedAt)) {
            return;
        }
        if (referralConversionRepository.existsByCampaignIdAndReferredTenantId(
                referralCampaign.getId(), referredTenantId)) {
            return;
        }
        ReferralConversionEntity referralConversion =
                new ReferralConversionEntity(
                        UUID.randomUUID(),
                        referralCampaign.getId(),
                        referralCode.getId(),
                        referredTenantId,
                        qualifiedAt,
                        clock.instant());
        try {
            referralConversionRepository.saveAndFlush(referralConversion);
        } catch (DataIntegrityViolationException duplicateConversion) {
            // Unique(campaign_id, referred_tenant_id) makes OAuth callback retries idempotent.
        }
    }

    @Transactional(readOnly = true)
    public boolean canAcceptReferralCode(String code, Instant now) {
        return referralCodeRepository
                .findByCode(code)
                .filter(referralCode -> referralCode.getStatus() == ReferralCodeStatus.ACTIVE)
                .flatMap(
                        referralCode ->
                                referralCampaignRepository
                                        .findById(referralCode.getCampaignId())
                                        .filter(
                                                referralCampaign ->
                                                        referralCampaign.getStatus()
                                                                        == ReferralCampaignStatus
                                                                                .ACTIVE
                                                                && !now.isBefore(
                                                                        referralCampaign
                                                                                .getStartsAt())
                                                                && now.isBefore(
                                                                        referralCampaign
                                                                                .getEndsAt())))
                .isPresent();
    }

    private ReferralTenantCode createTenantCode(UUID campaignId, UUID ownerTenantId) {
        campaignOrThrow(campaignId);
        ReferralCodeEntity referralCode =
                new ReferralCodeEntity(
                        UUID.randomUUID(),
                        campaignId,
                        ownerTenantId,
                        nextUniqueCode(),
                        clock.instant());
        return referralCodeRepository.saveAndFlush(referralCode).toTenantCode();
    }

    private String nextUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt += 1) {
            long randomValue = secureRandom.nextLong() & Long.MAX_VALUE;
            String code = "ZM" + Long.toString(randomValue, 36).toUpperCase(Locale.ROOT);
            if (!referralCodeRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate unique referral code");
    }

    private byte[] validateBannerImageBytes(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ReferralCampaignBannerInvalidException("Banner image is empty");
        }
        if (imageBytes.length > MAX_BANNER_IMAGE_BYTES) {
            throw new ReferralCampaignBannerInvalidException("Banner image is larger than 5 MB");
        }
        return imageBytes.clone();
    }

    private String validateBannerContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new ReferralCampaignBannerInvalidException("Banner image content type is empty");
        }
        String normalizedContentType = contentType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_BANNER_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new ReferralCampaignBannerInvalidException(
                    "Banner image content type is not supported");
        }
        return normalizedContentType;
    }

    private void ensureNoOtherActiveCampaign(UUID campaignId) {
        boolean activeCampaignExists =
                campaignId == null
                        ? referralCampaignRepository.existsByStatus(ReferralCampaignStatus.ACTIVE)
                        : referralCampaignRepository.existsByStatusAndIdNot(
                                ReferralCampaignStatus.ACTIVE, campaignId);
        if (activeCampaignExists) {
            throw new ReferralCampaignActiveConflictException();
        }
    }

    private ReferralCampaignEntity campaignOrThrow(UUID campaignId) {
        return referralCampaignRepository
                .findById(campaignId)
                .orElseThrow(
                        () ->
                                new ReferralCampaignNotFoundException(
                                        "Referral campaign not found: " + campaignId));
    }
}
