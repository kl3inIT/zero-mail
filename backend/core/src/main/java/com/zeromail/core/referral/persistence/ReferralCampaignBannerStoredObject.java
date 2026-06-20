package com.zeromail.core.referral.persistence;

import java.util.Objects;

public record ReferralCampaignBannerStoredObject(
        String objectKey, String contentType, int sizeBytes) {

    public ReferralCampaignBannerStoredObject {
        Objects.requireNonNull(objectKey, "objectKey must not be null");
        if (objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        Objects.requireNonNull(contentType, "contentType must not be null");
        if (contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
    }
}
