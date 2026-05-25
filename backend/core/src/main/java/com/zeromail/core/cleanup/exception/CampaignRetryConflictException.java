package com.zeromail.core.cleanup.exception;

import com.zeromail.core.cleanup.domain.UnsubscribeAttemptState;
import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when a user attempts to retry an unsubscribe attempt that is in a non-retryable state
 * (UNS-06). Retry is only valid from {@code FAILED}; retrying a {@code RUNNING} or {@code OK}
 * attempt is a conflict.
 *
 * <p>Privacy invariant (UNS-09): the message and params embed only the sender domain extracted from
 * the email, never the full sender address.
 */
public class CampaignRetryConflictException extends BusinessException {

    private final UUID campaignId;
    private final String senderDomain;
    private final UnsubscribeAttemptState currentState;

    public CampaignRetryConflictException(
            UUID campaignId, String senderEmail, UnsubscribeAttemptState currentState) {
        super(
                "Retry conflict: campaignId=%s senderDomain=%s currentState=%s"
                        .formatted(
                                requireUuid(campaignId, "campaignId"),
                                extractDomain(requireText(senderEmail, "senderEmail")),
                                requireState(currentState).id()));
        this.campaignId = campaignId;
        this.senderDomain = extractDomain(senderEmail);
        this.currentState = currentState;
    }

    public UUID campaignId() {
        return campaignId;
    }

    public String senderDomain() {
        return senderDomain;
    }

    public UnsubscribeAttemptState currentState() {
        return currentState;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.CAMPAIGN_RETRY_CONFLICT;
    }

    @Override
    public String logEvent() {
        return "cleanup_campaign_retry_conflict";
    }

    @Override
    public String title() {
        return "Retry conflict";
    }

    @Override
    public String detail() {
        return "The attempt cannot be retried in its current state.";
    }

    @Override
    public Map<String, Object> params() {
        return Map.of(
                "campaignId", campaignId.toString(),
                "currentState", currentState.id());
    }

    private static String extractDomain(String senderEmail) {
        int atIndex = senderEmail.indexOf('@');
        if (atIndex < 0 || atIndex == senderEmail.length() - 1) {
            return "unknown";
        }
        return senderEmail.substring(atIndex + 1);
    }

    private static UUID requireUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    private static UnsubscribeAttemptState requireState(UnsubscribeAttemptState state) {
        if (state == null) {
            throw new IllegalArgumentException("currentState must not be null");
        }
        return state;
    }
}
