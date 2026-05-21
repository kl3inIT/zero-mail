package com.zeromail.core.admin.mkey.exception;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;

/**
 * Raised when an admin attempts a master-key write without first minting a short-lived edit session
 * via {@code POST /master-keys/{provider}/edit-session}.
 */
public class EditSessionRequiredException extends AdminBusinessException {

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.master_key_edit_session_required";
    }

    @Override
    public String logEvent() {
        return "admin_master_key_edit_session_required";
    }

    @Override
    public String detail() {
        return "An open master-key edit session is required before performing this action.";
    }
}
