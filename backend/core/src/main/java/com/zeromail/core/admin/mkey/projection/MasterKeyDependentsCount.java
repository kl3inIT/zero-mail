package com.zeromail.core.admin.mkey.projection;

import com.zeromail.core.admin.mkey.domain.LlmProvider;

public record MasterKeyDependentsCount(LlmProvider provider, long count) {}
