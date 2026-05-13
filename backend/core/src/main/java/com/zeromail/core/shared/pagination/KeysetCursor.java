package com.zeromail.core.shared.pagination;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record KeysetCursor(Instant timestamp, String id, boolean isNullsLast) {

    private static final String VERSION = "v1";
    private static final String NULLS_LAST_TOKEN = "NULLS_LAST";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public KeysetCursor {
        if (!isNullsLast) {
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }
        id = requireText(id, "id");
    }

    public static String encode(Instant timestamp, UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return encode(timestamp, id.toString());
    }

    public static String encode(Instant timestamp, String id) {
        Instant cursorTimestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        String cursorId = requireText(id, "id");
        return encodePayload(
                VERSION
                        + ":"
                        + cursorTimestamp.getEpochSecond()
                        + ":"
                        + cursorTimestamp.getNano()
                        + ":"
                        + cursorId);
    }

    public static String nullsLast(String id) {
        return encodePayload(VERSION + ":" + NULLS_LAST_TOKEN + "::" + requireText(id, "id"));
    }

    public static Optional<KeysetCursor> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = new String(DECODER.decode(cursor.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalArgumentException("cursor must be valid base64url", invalidBase64);
        }
        String[] parts = payload.split(":", 4);
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("cursor has an unsupported format");
        }
        if (NULLS_LAST_TOKEN.equals(parts[1])) {
            if (!parts[2].isEmpty()) {
                throw new IllegalArgumentException("nulls-last cursor has an invalid timestamp");
            }
            return Optional.of(new KeysetCursor(null, parts[3], true));
        }
        try {
            long epochSecond = Long.parseLong(parts[1]);
            int nano = Integer.parseInt(parts[2]);
            return Optional.of(
                    new KeysetCursor(Instant.ofEpochSecond(epochSecond, nano), parts[3], false));
        } catch (NumberFormatException invalidTimestamp) {
            throw new IllegalArgumentException("cursor timestamp is malformed", invalidTimestamp);
        }
    }

    private static String encodePayload(String payload) {
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}
