package com.zeromail.core.admin.mkey.exception;

import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.Map;

/**
 * Raised when the pre-flight connectivity probe against a candidate master key returns a non-OK
 * {@link MasterKeyTestResult}. Carries the result enum so the frontend can render a precise error.
 */
public class MasterKeyTestFailedException extends AdminBusinessException {

    private final MasterKeyTestResult result;

    public MasterKeyTestFailedException(MasterKeyTestResult result) {
        super("Master key test failed");
        this.result = result;
    }

    public MasterKeyTestResult result() {
        return result;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "error.admin.master_key_test_failed";
    }

    @Override
    public String logEvent() {
        return "admin_master_key_test_failed";
    }

    @Override
    public String detail() {
        return "The master-key connectivity probe did not return OK.";
    }

    @Override
    public Map<String, Object> params() {
        return Map.of("result", result);
    }
}
