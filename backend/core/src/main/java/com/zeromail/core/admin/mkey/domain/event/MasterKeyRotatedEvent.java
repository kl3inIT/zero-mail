package com.zeromail.core.admin.mkey.domain.event;

import com.zeromail.core.llm.domain.LlmProvider;

public record MasterKeyRotatedEvent(LlmProvider provider, long providerSecretVersion) {}
