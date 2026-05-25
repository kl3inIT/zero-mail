package com.zeromail.core.llm.usecases;

import java.util.Arrays;

public record LlmProviderCredential(
        String providerId,
        String keyFormat,
        String baseUrl,
        byte[] plaintextKey,
        LlmCredentialSource source) {

    public LlmProviderCredential {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (keyFormat == null || keyFormat.isBlank()) {
            throw new IllegalArgumentException("keyFormat must not be blank");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (plaintextKey == null || plaintextKey.length == 0) {
            throw new IllegalArgumentException("plaintextKey must not be empty");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        plaintextKey = Arrays.copyOf(plaintextKey, plaintextKey.length);
    }

    @Override
    public byte[] plaintextKey() {
        return Arrays.copyOf(plaintextKey, plaintextKey.length);
    }

    public void wipe() {
        Arrays.fill(plaintextKey, (byte) 0);
    }
}
