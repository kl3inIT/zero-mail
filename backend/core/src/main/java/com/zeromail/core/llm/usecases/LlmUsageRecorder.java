package com.zeromail.core.llm.usecases;

public interface LlmUsageRecorder {

    void record(LlmUsageRecord usageRecord);
}
