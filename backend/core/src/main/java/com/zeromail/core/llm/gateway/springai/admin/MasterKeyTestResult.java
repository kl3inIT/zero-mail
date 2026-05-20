package com.zeromail.core.llm.gateway.springai.admin;

public enum MasterKeyTestResult {
    OK,
    INVALID_KEY,
    RATE_LIMITED,
    NETWORK_ERROR,
    TIMEOUT
}
