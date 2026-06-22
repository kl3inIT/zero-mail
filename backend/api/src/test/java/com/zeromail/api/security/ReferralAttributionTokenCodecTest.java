package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReferralAttributionTokenCodecTest {

    @Test
    void encodedTokenRoundTripsReferralAttribution() {
        ReferralAttributionTokenCodec tokenCodec =
                new ReferralAttributionTokenCodec("test-referral-signing-secret");
        ReferralAttributionSnapshot attribution =
                new ReferralAttributionSnapshot(
                        "ZME9XXKQX1ZL8K", Instant.parse("2026-06-22T02:45:22Z"));

        String token = tokenCodec.encode(attribution);

        assertThat(tokenCodec.decode(token)).hasValue(attribution);
    }

    @Test
    void tamperedTokenIsRejected() {
        ReferralAttributionTokenCodec tokenCodec =
                new ReferralAttributionTokenCodec("test-referral-signing-secret");
        ReferralAttributionSnapshot attribution =
                new ReferralAttributionSnapshot(
                        "ZME9XXKQX1ZL8K", Instant.parse("2026-06-22T02:45:22Z"));
        String token = tokenCodec.encode(attribution);
        char replacementCharacter = token.charAt(token.length() - 1) == 'A' ? 'B' : 'A';
        String tamperedToken = token.substring(0, token.length() - 1) + replacementCharacter;

        assertThat(tokenCodec.decode(tamperedToken)).isEmpty();
    }
}
