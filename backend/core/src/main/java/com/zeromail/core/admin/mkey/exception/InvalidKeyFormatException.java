package com.zeromail.core.admin.mkey.exception;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Raised when the supplied plaintext key does not match the provider's accepted credential format
 * (e.g. an Anthropic key shape against the OpenAI provider).
 */
public class InvalidKeyFormatException extends AdminBusinessException {

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.master_key_invalid_format";
    }

    @Override
    public String logEvent() {
        return "admin_master_key_invalid_format";
    }

    @Override
    public String detail() {
        return "The supplied master key does not match the required format.";
    }
}
