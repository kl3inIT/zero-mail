package com.zeromail.core.referral.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum ReferralCampaignStatus implements IdentifiedEnum {
    DRAFT,
    ACTIVE,
    PAUSED,
    ENDED,
    ARCHIVED;

    @Override
    public String id() {
        return name();
    }

    public static ReferralCampaignStatus fromId(String id) {
        return Stream.of(values())
                .filter(status -> status.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown ReferralCampaignStatus id: " + id));
    }
}
