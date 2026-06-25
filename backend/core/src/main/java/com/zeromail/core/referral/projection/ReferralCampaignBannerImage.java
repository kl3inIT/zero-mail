package com.zeromail.core.referral.projection;

import java.util.Objects;

public record ReferralCampaignBannerImage(byte[] bytes, String contentType, int sizeBytes) {

    public ReferralCampaignBannerImage {
        Objects.requireNonNull(bytes, "bytes must not be null");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
        Objects.requireNonNull(contentType, "contentType must not be null");
        if (contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (sizeBytes != bytes.length) {
            throw new IllegalArgumentException("sizeBytes must match bytes length");
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
