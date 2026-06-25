package com.zeromail.api.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ReferralAttributionSnapshot(String code, Instant attributedAt)
        implements Serializable {

    public static final String QUERY_PARAMETER = "ref";
    public static final String ATTRIBUTE_CODE = "referralCode";
    public static final String ATTRIBUTE_ATTRIBUTED_AT_EPOCH_MILLIS =
            "referralAttributedAtEpochMillis";
    public static final String CALLBACK_SESSION_ATTRIBUTE = "ZEROMAIL_OAUTH_REFERRAL_ATTRIBUTION";

    public ReferralAttributionSnapshot {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        code = code.trim();
        attributedAt = Objects.requireNonNull(attributedAt, "attributedAt must not be null");
    }

    public static ReferralAttributionSnapshot from(ReferralAttributionCookie.Attribution cookie) {
        return new ReferralAttributionSnapshot(cookie.code(), cookie.attributedAt());
    }

    public static Optional<ReferralAttributionSnapshot> fromAttributes(
            Map<String, Object> attributes) {
        Object codeValue = attributes.get(ATTRIBUTE_CODE);
        Object attributedAtValue = attributes.get(ATTRIBUTE_ATTRIBUTED_AT_EPOCH_MILLIS);
        if (!(codeValue instanceof String code) || code.isBlank()) {
            return Optional.empty();
        }
        Long attributedAtEpochMillis = epochMillisFrom(attributedAtValue);
        if (attributedAtEpochMillis == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    new ReferralAttributionSnapshot(
                            code, Instant.ofEpochMilli(attributedAtEpochMillis)));
        } catch (RuntimeException invalidAttribution) {
            return Optional.empty();
        }
    }

    private static Long epochMillisFrom(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String textValue && !textValue.isBlank()) {
            try {
                return Long.parseLong(textValue);
            } catch (NumberFormatException invalidEpochMillis) {
                return null;
            }
        }
        return null;
    }
}
