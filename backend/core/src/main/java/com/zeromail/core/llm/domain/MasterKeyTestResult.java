package com.zeromail.core.llm.domain;

public enum MasterKeyTestResult {
    OK,
    INVALID_KEY,
    RATE_LIMITED,
    NETWORK_ERROR,
    TIMEOUT
}
