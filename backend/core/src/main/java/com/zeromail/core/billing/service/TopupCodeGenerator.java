package com.zeromail.core.billing.service;

import java.security.SecureRandom;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/** Generates 8-character Crockford-base32 top-up codes for bank-transfer memos. */
@Component
public class TopupCodeGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateUniqueCode(Predicate<String> isAvailable, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidateCode = newCode();
            if (isAvailable.test(candidateCode)) {
                return candidateCode;
            }
        }
        throw new IllegalStateException(
                "Failed to generate unique top-up code after " + maxAttempts + " attempts");
    }

    private String newCode() {
        char[] codeCharacters = new char[CODE_LENGTH];
        for (int characterIndex = 0; characterIndex < CODE_LENGTH; characterIndex++) {
            codeCharacters[characterIndex] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(codeCharacters);
    }
}
