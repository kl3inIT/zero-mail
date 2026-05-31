package com.zeromail.core.llm.usecases;

import java.util.UUID;

@FunctionalInterface
public interface SensitiveDataProtectionDecider {

    boolean isSensitiveDataProtectionEnabled(UUID tenantId);
}
