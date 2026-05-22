package com.zeromail.core.admin.mkey.domain.event;

import com.zeromail.core.admin.mkey.domain.LlmProvider;

public record MasterKeyRotatedEvent(LlmProvider provider, long providerSecretVersion) {}
