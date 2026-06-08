package com.zeromail.core.messaging.telegram.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PairingCodeServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");
    private static final byte[] SECRET = new byte[32];

    @Test
    void mintAndVerify_returnsTenantIdWithinTelegramStartLimit() {
        UUID tenantId = UUID.randomUUID();
        PairingCodeService pairingCodeService = serviceAt(NOW);

        PairingCodeService.MintedPairingCode mintedPairingCode = pairingCodeService.mint(tenantId);
        PairingCodeService.ConsumedPairingCode consumedPairingCode =
                pairingCodeService.verify(mintedPairingCode.code());

        assertThat(mintedPairingCode.code()).hasSizeLessThanOrEqualTo(64);
        assertThat(mintedPairingCode.deeplink()).startsWith("https://t.me/ZeroMailBot?start=");
        assertThat(consumedPairingCode.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void verify_rejectsExpiredCode() {
        PairingCodeService mintedAtStart = serviceAt(NOW);
        String code = mintedAtStart.mint(UUID.randomUUID()).code();
        PairingCodeService verifier = serviceAt(NOW.plusSeconds(601));

        assertThatThrownBy(() -> verifier.verify(code))
                .isInstanceOf(PairingCodeExpiredException.class);
    }

    private static PairingCodeService serviceAt(Instant instant) {
        TelegramProperties properties =
                new TelegramProperties(
                        true,
                        "token",
                        "ZeroMailBot",
                        "telegram-bot",
                        "webhook-secret",
                        Base64.getEncoder().encodeToString(SECRET),
                        URI.create("https://api.telegram.org"),
                        URI.create("https://app.zeromail.test"),
                        false,
                        null);
        return new PairingCodeService(
                properties,
                Clock.fixed(instant, ZoneOffset.UTC),
                new SecureRandom(new byte[] {1, 2, 3, 4}));
    }
}
