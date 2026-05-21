package com.zeromail.core.admin.mkey.exception;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Raised when an admin requests detail for a provider that has no {@code llm_provider_master_key}
 * row of any priority/status.
 */
public class MissingMasterKeyRowException extends AdminBusinessException {

    public MissingMasterKeyRowException(LlmProvider provider) {
        super("Missing master key row for provider " + provider.id());
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.NOT_FOUND;
    }

    @Override
    public String errorCode() {
        return "error.admin.master_key_missing";
    }

    @Override
    public String logEvent() {
        return "admin_master_key_missing";
    }

    @Override
    public String detail() {
        return "No master key has been configured for the requested provider.";
    }
}
