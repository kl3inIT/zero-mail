package com.zeromail.core.llm.routing;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record PlatformLlmRouteCredentials(
        String providerId,
        UUID keyId,
        byte[] plaintextKey,
        String keyFormat,
        String baseUrl,
        long providerSecretVersion,
        long providerCatalogVersion) {

    public PlatformLlmRouteCredentials {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        Objects.requireNonNull(keyId, "keyId");
        if (plaintextKey == null || plaintextKey.length == 0) {
            throw new IllegalArgumentException("plaintextKey must not be empty");
        }
        if (keyFormat == null || keyFormat.isBlank()) {
            throw new IllegalArgumentException("keyFormat must not be blank");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
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
