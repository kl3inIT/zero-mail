package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import java.util.List;

public record ConnectionTestResult(MasterKeyTestResult result, List<String> models) {

    public ConnectionTestResult {
        models = models == null ? List.of() : List.copyOf(models);
    }
}
