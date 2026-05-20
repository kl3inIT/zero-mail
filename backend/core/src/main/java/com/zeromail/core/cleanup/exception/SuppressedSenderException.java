package com.zeromail.core.cleanup.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.Map;
import java.util.UUID;

/**
 * Raised when a campaign would target a sender that is already on the tenant's suppression list.
 * Suppressed senders are off-limits for further automation — adding them to a new campaign would
 * silently re-archive messages the user explicitly chose not to touch.
 *
 * <p>Privacy invariant (UNS-09): the message embeds the sender domain only, never the full sender
 * address. The exception's {@link #params()} also avoids the raw email.
 */
public class SuppressedSenderException extends BusinessException {

    private final UUID tenantId;
    private final String senderDomain;

    public SuppressedSenderException(UUID tenantId, String senderEmail) {
        super(
                "Suppressed sender: tenantId=%s senderDomain=%s"
                        .formatted(
                                requireUuid(tenantId),
                                extractDomain(requireText(senderEmail, "senderEmail"))));
        this.tenantId = tenantId;
        this.senderDomain = extractDomain(senderEmail);
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String senderDomain() {
        return senderDomain;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.SENDER_SUPPRESSED;
    }

    @Override
    public String logEvent() {
        return "cleanup_sender_suppressed";
    }

    @Override
    public String title() {
        return "Sender suppressed";
    }

    @Override
    public String detail() {
        return "The targeted sender is on the tenant's suppression list.";
    }

    @Override
    public Map<String, Object> params() {
        return Map.of("senderDomain", senderDomain);
    }

    private static String extractDomain(String senderEmail) {
        int atIndex = senderEmail.indexOf('@');
        if (atIndex < 0 || atIndex == senderEmail.length() - 1) {
            return "unknown";
        }
        return senderEmail.substring(atIndex + 1);
    }

    private static UUID requireUuid(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        return value;
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }
}
