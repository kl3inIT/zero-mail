package com.zeromail.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReferralAttributionTokenCodec {

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SIGNING_SECRET_BYTES = 16;
    private static final int EXPECTED_SIGNATURE_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 512;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec signingKey;

    public ReferralAttributionTokenCodec(
            @Value(
                            "${zero-mail.api.referral.attribution-token-signing-secret:${spring.security.oauth2.client.registration.google.client-secret:}}")
                    String signingSecret) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalStateException(
                    "zero-mail.api.referral.attribution-token-signing-secret must be configured");
        }
        byte[] signingSecretBytes = signingSecret.getBytes(StandardCharsets.UTF_8);
        if (signingSecretBytes.length < MIN_SIGNING_SECRET_BYTES) {
            throw new IllegalStateException(
                    "zero-mail.api.referral.attribution-token-signing-secret must be at least "
                            + MIN_SIGNING_SECRET_BYTES
                            + " bytes");
        }
        signingKey = new SecretKeySpec(signingSecretBytes, HMAC_ALGORITHM);
    }

    public String encode(ReferralAttributionSnapshot attribution) {
        Objects.requireNonNull(attribution, "attribution must not be null");
        String payload =
                VERSION
                        + "\n"
                        + attribution.code()
                        + "\n"
                        + attribution.attributedAt().toEpochMilli();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(payloadBytes) + "." + ENCODER.encodeToString(sign(payload));
    }

    public Optional<ReferralAttributionSnapshot> decode(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        String[] tokenParts = token.split("\\.", -1);
        if (tokenParts.length != 2 || tokenParts[0].isBlank() || tokenParts[1].isBlank()) {
            return Optional.empty();
        }
        byte[] payloadBytes;
        byte[] providedSignatureBytes;
        try {
            payloadBytes = DECODER.decode(tokenParts[0]);
            providedSignatureBytes = DECODER.decode(tokenParts[1]);
        } catch (IllegalArgumentException invalidBase64) {
            return Optional.empty();
        }
        if (providedSignatureBytes.length != EXPECTED_SIGNATURE_BYTES) {
            return Optional.empty();
        }
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        if (!validSignature(payload, providedSignatureBytes)) {
            return Optional.empty();
        }
        String[] payloadParts = payload.split("\n", -1);
        if (payloadParts.length != 3 || !VERSION.equals(payloadParts[0])) {
            return Optional.empty();
        }
        String code = payloadParts[1];
        try {
            Instant attributedAt = Instant.ofEpochMilli(Long.parseLong(payloadParts[2]));
            return Optional.of(new ReferralAttributionSnapshot(code, attributedAt));
        } catch (RuntimeException invalidPayload) {
            return Optional.empty();
        }
    }

    private boolean validSignature(String payload, byte[] providedSignatureBytes) {
        byte[] expectedSignatureBytes = sign(payload);
        return MessageDigest.isEqual(expectedSignatureBytes, providedSignatureBytes);
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception cryptoFailure) {
            throw new IllegalStateException(
                    "Unable to sign referral attribution token", cryptoFailure);
        }
    }
}
