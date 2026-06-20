package com.zeromail.core.referral.persistence;

import com.zeromail.core.referral.domain.ReferralCodeStatus;
import com.zeromail.core.referral.projection.ReferralTenantCode;
import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referral_code")
public class ReferralCodeEntity extends AbstractEntity {

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "owner_tenant_id", nullable = false)
    private UUID ownerTenantId;

    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReferralCodeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReferralCodeEntity() {}

    public ReferralCodeEntity(
            UUID id, UUID campaignId, UUID ownerTenantId, String code, Instant createdAt) {
        super(id);
        this.campaignId = campaignId;
        this.ownerTenantId = ownerTenantId;
        this.code = code;
        this.status = ReferralCodeStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getOwnerTenantId() {
        return ownerTenantId;
    }

    public String getCode() {
        return code;
    }

    public ReferralCodeStatus getStatus() {
        return status;
    }

    public ReferralTenantCode toTenantCode() {
        return new ReferralTenantCode(
                getId(), campaignId, ownerTenantId, code, status.id(), createdAt);
    }
}
