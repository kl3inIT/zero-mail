package com.zeromail.core.admin.mkey.exception;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.llm.domain.LlmProvider;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Raised when a reorder request omits or invents key IDs — the supplied ordering must exactly match
 * the provider's current set of credential rows (no additions, no removals, no duplicates).
 */
public class ProviderKeyReorderMismatchException extends AdminBusinessException {

    public ProviderKeyReorderMismatchException(LlmProvider provider) {
        super("Reorder payload does not match the provider's current key set: " + provider.id());
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.master_key_reorder_mismatch";
    }

    @Override
    public String logEvent() {
        return "admin_master_key_reorder_mismatch";
    }

    @Override
    public String detail() {
        return "Reorder payload must include every existing key and no extras.";
    }
}
