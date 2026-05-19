package com.zeromail.core.admin.auth.usecases;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EnrollmentTokenService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentTokenService.class);
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(10);
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final Clock clock;
    private final Duration tokenTtl;
    private final SecureRandom secureRandom;
    private final Map<String, EnrollmentTokenEntry> tokenEntries = new ConcurrentHashMap<>();

    public EnrollmentTokenService() {
        this(Clock.systemUTC(), DEFAULT_TOKEN_TTL, new SecureRandom());
    }

    EnrollmentTokenService(Clock clock, Duration tokenTtl, SecureRandom secureRandom) {
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.secureRandom = secureRandom;
    }

    public String mintToken(UUID adminUserId, String email) {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);
        Instant expiresAt = clock.instant().plus(tokenTtl);
        tokenEntries.put(
                token, new EnrollmentTokenEntry(adminUserId, normalizeEmail(email), expiresAt));
        log.info("event=admin_enrollment_token_minted adminUserId={}", adminUserId);
        return token;
    }

    public Optional<UUID> consume(String token, String email) {
        EnrollmentTokenEntry tokenEntry = tokenEntries.remove(token);
        if (tokenEntry == null) {
            return Optional.empty();
        }
        if (tokenEntry.expiresAt().isBefore(clock.instant())
                || !tokenEntry.email().equals(normalizeEmail(email))) {
            return Optional.empty();
        }
        return Optional.of(tokenEntry.adminUserId());
    }

    @Scheduled(fixedDelay = 60_000)
    public void sweepExpiredTokens() {
        Instant now = clock.instant();
        tokenEntries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record EnrollmentTokenEntry(UUID adminUserId, String email, Instant expiresAt) {}
}
