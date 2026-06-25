package com.zeromail.core.referral.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class ReferralCampaignActiveConflictException extends BusinessException {

    public ReferralCampaignActiveConflictException() {
        super("Only one referral campaign can be active");
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.REFERRAL_CAMPAIGN_ACTIVE_CONFLICT;
    }

    @Override
    public String logEvent() {
        return "referral_campaign_active_conflict";
    }

    @Override
    public String title() {
        return "Referral campaign active conflict";
    }

    @Override
    public String detail() {
        return "Only one referral campaign can be active at a time.";
    }
}
