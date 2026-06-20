package com.zeromail.core.referral.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

public class ReferralCampaignBannerInvalidException extends BusinessException {

    public ReferralCampaignBannerInvalidException(String message) {
        super(message);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.REFERRAL_CAMPAIGN_BANNER_INVALID;
    }

    @Override
    public String logEvent() {
        return "referral_campaign_banner_invalid";
    }

    @Override
    public String title() {
        return "Referral campaign banner is invalid";
    }

    @Override
    public String detail() {
        return "The uploaded referral campaign banner is not a supported image.";
    }
}
