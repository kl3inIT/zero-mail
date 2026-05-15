package com.zeromail.core.triage.usecases;

import com.zeromail.core.shared.crypto.Hashing;
import com.zeromail.core.shared.privacy.EmailAddressCanonicalizer;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class SenderEmailCanonicalizer {

    private final EmailAddressCanonicalizer emailAddressCanonicalizer;

    public SenderEmailCanonicalizer(EmailAddressCanonicalizer emailAddressCanonicalizer) {
        this.emailAddressCanonicalizer = emailAddressCanonicalizer;
    }

    public String canonicalize(String rawSenderEmail) {
        return emailAddressCanonicalizer.canonicalize(rawSenderEmail);
    }

    public String redisCacheKeyComponent(String canonicalizedEmail) {
        return HexFormat.of()
                .formatHex(Hashing.sha256(requireCanonicalizedEmail(canonicalizedEmail)));
    }

    public String gmailSearchToken(String canonicalizedEmail) {
        String safeAddress =
                requireCanonicalizedEmail(canonicalizedEmail)
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"");
        return "\"" + safeAddress + "\"";
    }

    private static String requireCanonicalizedEmail(String canonicalizedEmail) {
        if (canonicalizedEmail == null || canonicalizedEmail.isBlank()) {
            throw new IllegalArgumentException("canonicalizedEmail must not be blank");
        }
        return canonicalizedEmail;
    }
}
