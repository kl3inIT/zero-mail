package com.zeromail.core.admin.mkey.usecases;

public enum MasterKeyTestResult {
    OK,
    INVALID_KEY,
    RATE_LIMITED,
    NETWORK_ERROR,
    TIMEOUT
}
