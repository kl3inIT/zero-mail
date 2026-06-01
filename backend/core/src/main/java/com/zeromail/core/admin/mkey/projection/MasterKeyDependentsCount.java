package com.zeromail.core.admin.mkey.projection;

import com.zeromail.core.llm.domain.LlmProvider;

public record MasterKeyDependentsCount(LlmProvider provider, long count) {}
