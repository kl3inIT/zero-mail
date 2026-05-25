package com.zeromail.core.admin.mkey.usecases;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.nio.charset.StandardCharsets;

public final class MasterKeyMasker {

    private MasterKeyMasker() {}

    public static String mask(byte[] plaintext, LlmProvider provider) {
        if (plaintext == null || plaintext.length == 0) {
            return null;
        }
        String plaintextKey = new String(plaintext, StandardCharsets.UTF_8);
        String suffix =
                plaintextKey.length() <= 4
                        ? plaintextKey
                        : plaintextKey.substring(plaintextKey.length() - 4);
        String prefix = prefixFor(plaintextKey, provider);
        return prefix + "****" + suffix;
    }

    private static String prefixFor(String plaintextKey, LlmProvider provider) {
        if (plaintextKey.startsWith("sk-ant-") || LlmProvider.ANTHROPIC.equals(provider)) {
            return "sk-ant-";
        }
        if (plaintextKey.startsWith("AIza") || LlmProvider.GOOGLE.equals(provider)) {
            return "AIza";
        }
        if (plaintextKey.startsWith("sk-or-")) {
            return "sk-or-";
        }
        return "sk-";
    }
}
