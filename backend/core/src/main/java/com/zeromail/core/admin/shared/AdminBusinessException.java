package com.zeromail.core.admin.shared;

import com.zeromail.core.shared.exception.BusinessException;

/**
 * Shared base for admin-module business exceptions. Hardcodes the {@code title} to "Admin request
 * rejected" — admin error responses NEVER carry detailed per-exception titles per the locked
 * privacy contract; the frontend switches on {@link #errorCode()} for localization.
 *
 * <p>Subclasses still declare {@link #errorClass()}, {@link #errorCode()}, {@link #logEvent()}, and
 * {@link #detail()}.
 */
public abstract class AdminBusinessException extends BusinessException {

    protected AdminBusinessException() {
        super();
    }

    protected AdminBusinessException(String message) {
        super(message);
    }

    protected AdminBusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public final String title() {
        return "Admin request rejected";
    }
}
