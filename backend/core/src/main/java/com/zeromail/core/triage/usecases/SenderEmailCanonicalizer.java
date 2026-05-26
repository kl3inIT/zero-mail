package com.zeromail.core.triage.usecases;

import com.zeromail.core.shared.crypto.Hashing;
import com.zeromail.core.shared.privacy.EmailAddressCanonicalizer;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity.PatternKind;
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

    public CanonicalizedPattern canonicalizePattern(String rawPattern) {
        if (rawPattern == null || rawPattern.isBlank()) {
            throw new IllegalArgumentException("rawPattern must not be blank");
        }
        String trimmedPattern = rawPattern.trim();
        if (trimmedPattern.startsWith("@")) {
            return new CanonicalizedPattern(PatternKind.DOMAIN, canonicalizeDomain(trimmedPattern));
        }
        return new CanonicalizedPattern(PatternKind.EMAIL, canonicalize(trimmedPattern));
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

    private static String canonicalizeDomain(String rawDomainPattern) {
        String canonicalDomainPattern = rawDomainPattern.toLowerCase(java.util.Locale.ROOT);
        String domainPart = canonicalDomainPattern.substring(1);
        if (domainPart.isBlank()
                || !domainPart.contains(".")
                || domainPart.contains("@")
                || domainPart.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "domain pattern must start with @ and include a domain");
        }
        return canonicalDomainPattern;
    }

    public record CanonicalizedPattern(PatternKind kind, String value) {

        public CanonicalizedPattern {
            if (kind == null) {
                throw new IllegalArgumentException("kind must not be null");
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("value must not be blank");
            }
        }
    }
}
