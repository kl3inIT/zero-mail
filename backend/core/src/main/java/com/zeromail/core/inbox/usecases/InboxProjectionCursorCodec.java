package com.zeromail.core.inbox.usecases;

import com.zeromail.core.shared.crypto.CryptoProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Opaque-encode the keyset cursor for {@code InboxProjectionReadService} page queries (Phase B Wave
 * 0).
 *
 * <p>The cursor is {@code (receivedAt, gmailMessageId)} — the partial index already orders by that
 * pair, so the next page is a single index range scan past the previous page's last row. Different
 * shape from the existing Gmail {@code pageToken} cursor used by {@code RecentInboxReadService}, so
 * this codec is deliberately separate; Wave 1 will pick which encoding to use at the orchestrator
 * boundary.
 *
 * <p>Format: {@code base64url(v1\n<epochMicros>\n<gmailMessageId>\n<HMAC-SHA256 signature>)}. The
 * signature uses the platform refresh-token signing key — same convention as the existing inbox
 * cursor codec; cursors are opaque pagination handles, not high-value crypto material.
 */
@Component
public class InboxProjectionCursorCodec {

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec signingKey;

    public InboxProjectionCursorCodec(CryptoProperties cryptoProperties) {
        Objects.requireNonNull(cryptoProperties, "cryptoProperties must not be null");
        byte[] decodedSigningKey;
        try {
            decodedSigningKey =
                    Base64.getDecoder().decode(cryptoProperties.refreshTokenKeyBase64());
        } catch (IllegalArgumentException invalidSigningKey) {
            throw new IllegalStateException(
                    "Invalid inbox projection cursor signing key", invalidSigningKey);
        }
        try {
            this.signingKey = new SecretKeySpec(decodedSigningKey, HMAC_ALGORITHM);
        } finally {
            Arrays.fill(decodedSigningKey, (byte) 0);
        }
    }

    /**
     * Decode an opaque cursor string. Returns {@link InboxProjectionCursor#firstPage()} for blank /
     * null input so the read service can treat "no cursor" and "first page" identically.
     *
     * @throws InvalidProjectionCursorException when the cursor is malformed, signature mismatches,
     *     or the embedded epoch micros are not parseable.
     */
    public InboxProjectionCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return InboxProjectionCursor.firstPage();
        }
        String payload;
        try {
            payload = new String(DECODER.decode(cursor.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalidBase64) {
            throw new InvalidProjectionCursorException(
                    "Cursor is not valid base64url", invalidBase64);
        }
        String[] parts = payload.split("\n", 4);
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            throw new InvalidProjectionCursorException(
                    "Cursor missing required v1 envelope (epochMicros, gmailMessageId, signature)");
        }
        String unsignedPayload = parts[0] + "\n" + parts[1] + "\n" + parts[2];
        if (!validSignature(unsignedPayload, parts[3])) {
            throw new InvalidProjectionCursorException("Cursor HMAC signature mismatch");
        }
        long epochMicros;
        try {
            epochMicros = Long.parseLong(parts[1]);
        } catch (NumberFormatException invalidEpoch) {
            throw new InvalidProjectionCursorException(
                    "Cursor receivedAt is not a valid long epoch micros", invalidEpoch);
        }
        if (parts[2].isBlank()) {
            throw new InvalidProjectionCursorException("Cursor gmailMessageId must not be blank");
        }
        Instant receivedAt =
                Instant.ofEpochSecond(
                        Math.floorDiv(epochMicros, 1_000_000L),
                        Math.floorMod(epochMicros, 1_000_000L) * 1_000L);
        return new InboxProjectionCursor(receivedAt, parts[2]);
    }

    /**
     * Encode a cursor pointing at the last row of the current page. The next page query will use
     * the embedded {@code (receivedAt, gmailMessageId)} as the keyset comparator.
     */
    public String encode(Instant receivedAt, String gmailMessageId) {
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        if (gmailMessageId.isBlank()) {
            throw new IllegalArgumentException("gmailMessageId must not be blank");
        }
        long epochMicros =
                Math.multiplyExact(receivedAt.getEpochSecond(), 1_000_000L)
                        + (receivedAt.getNano() / 1_000L);
        String unsignedPayload = VERSION + "\n" + epochMicros + "\n" + gmailMessageId;
        String payloadWithSignature = unsignedPayload + "\n" + signatureFor(unsignedPayload);
        return ENCODER.encodeToString(payloadWithSignature.getBytes(StandardCharsets.UTF_8));
    }

    private boolean validSignature(String payload, String actualSignature) {
        byte[] expectedSignatureBytes = signatureFor(payload).getBytes(StandardCharsets.UTF_8);
        byte[] actualSignatureBytes = actualSignature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedSignatureBytes, actualSignatureBytes);
    }

    private String signatureFor(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException cryptoException) {
            throw new IllegalStateException(
                    "Unable to sign inbox projection cursor", cryptoException);
        }
    }
}
