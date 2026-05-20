package com.zeromail.core.admin.auth.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentTokenServiceTest {

    @Test
    void token_is_hex_and_consumes_once_for_matching_email() {
        MutableClock mutableClock = new MutableClock(Instant.parse("2026-05-19T18:36:17Z"));
        EnrollmentTokenService enrollmentTokenService =
                new EnrollmentTokenService(
                        mutableClock, Duration.ofMinutes(10), new SecureRandom());
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000821");

        String token = enrollmentTokenService.mintToken(adminUserId, "Admin@Example.com");

        assertThat(token).matches("[0-9a-f]{64}");
        assertThat(enrollmentTokenService.consume(token, "admin@example.com"))
                .contains(adminUserId);
        assertThat(enrollmentTokenService.consume(token, "admin@example.com")).isEmpty();
    }

    @Test
    void expired_token_is_rejected_and_swept() {
        MutableClock mutableClock = new MutableClock(Instant.parse("2026-05-19T18:36:17Z"));
        EnrollmentTokenService enrollmentTokenService =
                new EnrollmentTokenService(
                        mutableClock, Duration.ofMinutes(10), new SecureRandom());
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000822");
        String token = enrollmentTokenService.mintToken(adminUserId, "admin@example.com");

        mutableClock.advance(Duration.ofMinutes(11));
        enrollmentTokenService.sweepExpiredTokens();

        assertThat(enrollmentTokenService.consume(token, "admin@example.com")).isEmpty();
    }

    private static final class MutableClock extends Clock {

        private Instant currentInstant;

        private MutableClock(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        private void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }
    }
}
