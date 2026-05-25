package com.zeromail.core.cleanup.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.Map;

/**
 * Raised when a campaign request exceeds one of the {@link
 * com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy} caps (UNS-03a / UNS-03b).
 *
 * <p>Carries the {@link Kind} discriminator so the HTTP layer maps to the correct error code
 * without case-splitting on exception type. The kind also drives the user-facing string the
 * frontend localizes.
 *
 * <p>Privacy: the message embeds the violating counts only, never sender emails — the validator
 * computes counts before invoking this exception.
 */
public class CampaignCapExceededException extends BusinessException {

    private final Kind kind;
    private final int actual;
    private final int cap;

    public CampaignCapExceededException(Kind kind, int actual, int cap) {
        super(
                "Campaign cap exceeded: kind=%s actual=%d cap=%d"
                        .formatted(requireKind(kind).name(), actual, cap));
        this.kind = kind;
        this.actual = actual;
        this.cap = cap;
    }

    public Kind kind() {
        return kind;
    }

    public int actual() {
        return actual;
    }

    public int cap() {
        return cap;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.UNPROCESSABLE;
    }

    @Override
    public String errorCode() {
        return kind.errorCode;
    }

    @Override
    public String logEvent() {
        return "cleanup_campaign_cap_exceeded";
    }

    @Override
    public String title() {
        return "Campaign cap exceeded";
    }

    @Override
    public String detail() {
        return kind.detail;
    }

    @Override
    public Map<String, Object> params() {
        return Map.of(
                "kind", kind.name(),
                "actual", actual,
                "cap", cap);
    }

    public enum Kind {
        TOO_MANY_SENDERS(
                ErrorCodes.CAMPAIGN_TOO_MANY_SENDERS,
                "The campaign targets more distinct senders than the per-campaign limit."),
        TOO_MANY_MESSAGES(
                ErrorCodes.CAMPAIGN_TOO_MANY_MESSAGES,
                "The campaign would archive more history messages than the per-campaign limit.");

        private final String errorCode;
        private final String detail;

        Kind(String errorCode, String detail) {
            this.errorCode = errorCode;
            this.detail = detail;
        }

        public String errorCode() {
            return errorCode;
        }
    }

    private static Kind requireKind(Kind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        return kind;
    }
}
