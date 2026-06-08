package com.zeromail.core.chat.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * A confirmed-send action (sendEmail / replyEmail / forwardEmail) carried a recipient field (to /
 * cc / bcc) that is not a parseable email address — typically the assistant filled it with a
 * person's display name instead of an address. Surfaced as HTTP 400 with a dedicated error code so
 * the frontend can tell the user to fix the recipient, rather than the generic "Bad request".
 *
 * <p>Privacy: the message and {@link #detail()} never echo the offending recipient value.
 */
public final class RecipientInvalidException extends BusinessException {

    public RecipientInvalidException() {
        super("Recipient address is not a valid email address.");
    }

    public RecipientInvalidException(Throwable cause) {
        super("Recipient address is not a valid email address.", cause);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.CHAT_RECIPIENT_INVALID;
    }

    @Override
    public String logEvent() {
        return "chat_recipient_invalid";
    }

    @Override
    public String title() {
        return "Recipient address is invalid";
    }

    @Override
    public String detail() {
        return "A recipient field must contain a valid email address.";
    }
}
