package com.zeromail.core.llm.usecases;

import com.zeromail.core.llm.domain.BYOKProvider;
import java.util.Arrays;

public record ByokChatCredential(
        BYOKProvider provider, String endpoint, String model, byte[] decryptedKey) {

    public ByokChatCredential {
        decryptedKey =
                decryptedKey == null ? null : Arrays.copyOf(decryptedKey, decryptedKey.length);
    }

    @Override
    public byte[] decryptedKey() {
        return decryptedKey == null ? null : Arrays.copyOf(decryptedKey, decryptedKey.length);
    }
}
