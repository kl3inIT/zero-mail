package com.zeromail.core.billing.domain;

import java.security.SecureRandom;
import java.util.function.Predicate;

public class BankTransferCodeGenerator {

    private static final char[] CROCKFORD_ALPHABET =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateUniqueCode(Predicate<String> isAvailable, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidateCode = generateCode();
            if (isAvailable.test(candidateCode)) {
                return candidateCode;
            }
        }
        throw new IllegalStateException("Unable to generate unique bank transfer code");
    }

    private String generateCode() {
        StringBuilder codeBuilder = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            codeBuilder.append(CROCKFORD_ALPHABET[secureRandom.nextInt(CROCKFORD_ALPHABET.length)]);
        }
        return codeBuilder.toString();
    }
}
