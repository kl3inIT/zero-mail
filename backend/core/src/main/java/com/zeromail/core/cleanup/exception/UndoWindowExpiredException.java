package com.zeromail.core.cleanup.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when a user attempts to undo a campaign past the {@link
 * com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy#UNDO_WINDOW} (UNS-07b).
 *
 * <p>Carries privacy-safe fields only: campaign UUID, applied timestamp, the window duration. No
 * sender list, no message contents.
 */
public class UndoWindowExpiredException extends BusinessException {

    private final UUID campaignId;
    private final Instant appliedAt;
    private final Duration windowDuration;

    public UndoWindowExpiredException(UUID campaignId, Instant appliedAt, Duration windowDuration) {
        super(
                "Undo window expired: campaignId=%s appliedAt=%s windowDuration=%s"
                        .formatted(
                                requireUuid(campaignId, "campaignId"),
                                requireInstant(appliedAt, "appliedAt"),
                                requireDuration(windowDuration, "windowDuration")));
        this.campaignId = campaignId;
        this.appliedAt = appliedAt;
        this.windowDuration = windowDuration;
    }

    public UUID campaignId() {
        return campaignId;
    }

    public Instant appliedAt() {
        return appliedAt;
    }

    public Duration windowDuration() {
        return windowDuration;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.CAMPAIGN_UNDO_WINDOW_EXPIRED;
    }

    @Override
    public String logEvent() {
        return "cleanup_campaign_undo_window_expired";
    }

    @Override
    public String title() {
        return "Undo window expired";
    }

    @Override
    public String detail() {
        return "The undo window for this campaign has elapsed.";
    }

    @Override
    public Map<String, Object> params() {
        return Map.of(
                "campaignId", campaignId.toString(),
                "windowDays", String.valueOf(windowDuration.toDays()));
    }

    private static UUID requireUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static Instant requireInstant(Instant value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static Duration requireDuration(Duration value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
