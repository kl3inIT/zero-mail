package com.zeromail.core.cleanup.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when the requested campaign id does not exist for the current tenant. The tenant-id scope
 * is enforced by the repository's {@code findByIdAndTenantId} — a campaign that belongs to another
 * tenant looks identical to "not found" by design (privacy invariant: never confirm existence to a
 * non-owner).
 */
public class CampaignNotFoundException extends BusinessException {

    private final UUID campaignId;

    public CampaignNotFoundException(UUID campaignId) {
        super("Campaign not found: campaignId=%s".formatted(requireUuid(campaignId)));
        this.campaignId = campaignId;
    }

    public UUID campaignId() {
        return campaignId;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.NOT_FOUND;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.CAMPAIGN_NOT_FOUND;
    }

    @Override
    public String logEvent() {
        return "cleanup_campaign_not_found";
    }

    @Override
    public String title() {
        return "Campaign not found";
    }

    @Override
    public String detail() {
        return "The requested campaign was not found.";
    }

    @Override
    public Map<String, Object> params() {
        return Map.of("campaignId", campaignId.toString());
    }

    private static UUID requireUuid(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("campaignId must not be null");
        }
        return value;
    }
}
