package com.zeromail.core.messaging.telegram.usecases;

import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PairingCodeService {

    private static final int PAYLOAD_SIZE = 28;
    private static final int SIGNATURE_SIZE = 16;
    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final TelegramProperties telegramProperties;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private volatile SecretKeySpec cachedSecretKey;

    @Autowired
    public PairingCodeService(TelegramProperties telegramProperties, Clock clock) {
        this(telegramProperties, clock, new SecureRandom());
    }

    PairingCodeService(
            TelegramProperties telegramProperties, Clock clock, SecureRandom secureRandom) {
        this.telegramProperties = telegramProperties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public MintedPairingCode mint(UUID tenantId) {
        Instant issuedAt = clock.instant();
        byte[] payload =
                ByteBuffer.allocate(PAYLOAD_SIZE)
                        .putLong(tenantId.getMostSignificantBits())
                        .putLong(tenantId.getLeastSignificantBits())
                        .putLong(secureRandom.nextLong())
                        .putInt((int) issuedAt.getEpochSecond())
                        .array();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String code = encoder.encodeToString(payload) + "." + encoder.encodeToString(sign(payload));
        String deeplink = "https://t.me/" + telegramProperties.botUsername() + "?start=" + code;
        return new MintedPairingCode(code, deeplink, issuedAt.plus(TIME_TO_LIVE));
    }

    public ConsumedPairingCode verify(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidPairingCodeException();
        }
        String[] parts = code.split("\\.");
        if (parts.length != 2) {
            throw new InvalidPairingCodeException();
        }
        byte[] payload;
        byte[] signature;
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            payload = decoder.decode(parts[0]);
            signature = decoder.decode(parts[1]);
        } catch (IllegalArgumentException invalidBase64) {
            throw new InvalidPairingCodeException();
        }
        if (payload.length != PAYLOAD_SIZE || signature.length != SIGNATURE_SIZE) {
            throw new InvalidPairingCodeException();
        }
        if (!MessageDigest.isEqual(signature, sign(payload))) {
            throw new InvalidPairingCodeException();
        }
        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
        UUID tenantId = new UUID(payloadBuffer.getLong(), payloadBuffer.getLong());
        payloadBuffer.getLong();
        Instant issuedAt = Instant.ofEpochSecond(Integer.toUnsignedLong(payloadBuffer.getInt()));
        if (clock.instant().isAfter(issuedAt.plus(TIME_TO_LIVE))) {
            throw new PairingCodeExpiredException();
        }
        return new ConsumedPairingCode(tenantId, issuedAt);
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey());
            return Arrays.copyOf(mac.doFinal(payload), SIGNATURE_SIZE);
        } catch (NoSuchAlgorithmException | InvalidKeyException cryptoFailure) {
            throw new IllegalStateException("HMAC-SHA256 must be available", cryptoFailure);
        }
    }

    private SecretKeySpec secretKey() {
        SecretKeySpec cached = cachedSecretKey;
        if (cached != null) {
            return cached;
        }
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(telegramProperties.messagingLinkSecret());
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalStateException(
                    "zero-mail.messaging.telegram.messaging-link-secret must be valid base64",
                    invalidBase64);
        }
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "zero-mail.messaging.telegram.messaging-link-secret must decode to >= 32 bytes");
        }
        cachedSecretKey = new SecretKeySpec(secretBytes, HMAC_ALGORITHM);
        return cachedSecretKey;
    }

    public record MintedPairingCode(String code, String deeplink, Instant expiresAt) {}

    public record ConsumedPairingCode(UUID tenantId, Instant issuedAt) {}
}
