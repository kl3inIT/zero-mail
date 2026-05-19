package com.zeromail.core.admin.mkey.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MasterKeyRateLimiterTest {

    @Test
    void edit_limit_allows_first_ten_and_sets_hour_ttl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(1L);
        MasterKeyRateLimiter rateLimiter =
                new MasterKeyRateLimiter(() -> redisTemplate, fixedClock());
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000111");

        rateLimiter.checkEditAllowed(actorId);

        long epochHour = fixedClock().instant().getEpochSecond() / 3600L;
        verify(redisTemplate)
                .expire(
                        eq("zeromail:mkey:edits:" + actorId + ":" + epochHour),
                        eq(Duration.ofHours(1)));
    }

    @Test
    void test_connection_limit_rejects_thirty_first_request() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(31L);
        MasterKeyRateLimiter rateLimiter =
                new MasterKeyRateLimiter(() -> redisTemplate, fixedClock());
        UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000111");

        assertThatThrownBy(() -> rateLimiter.checkTestConnectionAllowed(actorId))
                .isInstanceOf(MasterKeyRateLimiter.RateLimitExceededException.class);
    }

    private static java.time.Clock fixedClock() {
        return java.time.Clock.fixed(
                Instant.parse("2026-05-20T00:00:00Z"), java.time.ZoneOffset.UTC);
    }
}
