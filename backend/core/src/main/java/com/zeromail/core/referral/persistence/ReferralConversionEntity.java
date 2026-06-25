package com.zeromail.core.referral.persistence;

import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referral_conversion")
public class ReferralConversionEntity extends AbstractEntity {

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "referral_code_id", nullable = false)
    private UUID referralCodeId;

    @Column(name = "referred_tenant_id", nullable = false)
    private UUID referredTenantId;

    @Column(name = "qualified_at", nullable = false)
    private Instant qualifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReferralConversionEntity() {}

    public ReferralConversionEntity(
            UUID id,
            UUID campaignId,
            UUID referralCodeId,
            UUID referredTenantId,
            Instant qualifiedAt,
            Instant createdAt) {
        super(id);
        this.campaignId = campaignId;
        this.referralCodeId = referralCodeId;
        this.referredTenantId = referredTenantId;
        this.qualifiedAt = qualifiedAt;
        this.createdAt = createdAt;
    }
}
