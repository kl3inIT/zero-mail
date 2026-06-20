package com.zeromail.core.referral.persistence;

import com.zeromail.core.referral.domain.ReferralCampaignStatus;
import com.zeromail.core.referral.projection.ReferralCampaignBannerReference;
import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import com.zeromail.core.referral.usecases.ReferralCampaignCreateCommand;
import com.zeromail.core.referral.usecases.ReferralCampaignUpdateCommand;
import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "referral_campaign")
public class ReferralCampaignEntity extends AbstractEntity {

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "campaign_code", nullable = false, length = 80, unique = true)
    private String campaignCode;

    @Column(name = "slug", nullable = false, length = 140, unique = true)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReferralCampaignStatus status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "web_banner_enabled", nullable = false)
    private boolean webBannerEnabled;

    @Column(name = "countdown_enabled", nullable = false)
    private boolean countdownEnabled;

    @Column(name = "leaderboard_enabled", nullable = false)
    private boolean leaderboardEnabled;

    @Column(name = "leaderboard_limit", nullable = false)
    private int leaderboardLimit;

    @Column(name = "banner_image_object_key", length = 512)
    private String bannerImageObjectKey;

    @Column(name = "banner_image_content_type", length = 80)
    private String bannerImageContentType;

    @Column(name = "banner_image_size_bytes")
    private Integer bannerImageSizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected ReferralCampaignEntity() {}

    public ReferralCampaignEntity(
            UUID id, ReferralCampaignCreateCommand command, Instant createdAt) {
        super(id);
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        apply(command);
        this.status = command.status();
    }

    public ReferralCampaignSnapshot toSnapshot() {
        return new ReferralCampaignSnapshot(
                getId(),
                name,
                campaignCode,
                slug,
                description,
                status,
                startsAt,
                endsAt,
                webBannerEnabled,
                countdownEnabled,
                leaderboardEnabled,
                leaderboardLimit,
                hasBannerImage(),
                createdAt,
                updatedAt);
    }

    public ReferralCampaignStatus getStatus() {
        return status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void update(ReferralCampaignUpdateCommand command, Instant updatedAt) {
        apply(command);
        this.updatedAt = updatedAt;
    }

    public void setStatus(ReferralCampaignStatus status, Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public void setBannerImageReference(
            String objectKey, String contentType, int sizeBytes, Instant updatedAt) {
        this.bannerImageObjectKey = objectKey;
        this.bannerImageContentType = contentType;
        this.bannerImageSizeBytes = sizeBytes;
        this.updatedAt = updatedAt;
    }

    public Optional<ReferralCampaignBannerReference> bannerImageReference() {
        if (!hasBannerImage()) {
            return Optional.empty();
        }
        return Optional.of(
                new ReferralCampaignBannerReference(
                        bannerImageObjectKey, bannerImageContentType, bannerImageSizeBytes));
    }

    private boolean hasBannerImage() {
        return bannerImageObjectKey != null
                && !bannerImageObjectKey.isBlank()
                && bannerImageSizeBytes != null
                && bannerImageSizeBytes > 0;
    }

    private void apply(ReferralCampaignCreateCommand command) {
        this.name = command.name();
        this.campaignCode = command.campaignCode();
        this.slug = command.slug();
        this.description = command.description();
        this.startsAt = command.startsAt();
        this.endsAt = command.endsAt();
        this.webBannerEnabled = command.webBannerEnabled();
        this.countdownEnabled = command.countdownEnabled();
        this.leaderboardEnabled = command.leaderboardEnabled();
        this.leaderboardLimit = command.leaderboardLimit();
    }

    private void apply(ReferralCampaignUpdateCommand command) {
        this.name = command.name();
        this.campaignCode = command.campaignCode();
        this.slug = command.slug();
        this.description = command.description();
        this.startsAt = command.startsAt();
        this.endsAt = command.endsAt();
        this.webBannerEnabled = command.webBannerEnabled();
        this.countdownEnabled = command.countdownEnabled();
        this.leaderboardEnabled = command.leaderboardEnabled();
        this.leaderboardLimit = command.leaderboardLimit();
    }
}
