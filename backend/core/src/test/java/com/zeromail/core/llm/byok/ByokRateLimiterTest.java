package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ByokRateLimiterTest {

    @Test
    void allows_first_ten_tests_and_sets_window_ttl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(1L);
        ByokRateLimiter rateLimiter = new ByokRateLimiter(() -> redisTemplate, fixedClock());
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000009001");

        rateLimiter.requireAllowance(tenantId, "byok.test_connection", 10, Duration.ofHours(1));

        long epochHour = fixedClock().instant().getEpochSecond() / 3600L;
        verify(redisTemplate)
                .expire(
                        eq("zeromail:byok.test_connection:" + tenantId + ":" + epochHour),
                        eq(Duration.ofHours(1)));
    }

    @Test
    void rejects_eleventh_test_connection_attempt() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(11L);
        ByokRateLimiter rateLimiter = new ByokRateLimiter(() -> redisTemplate, fixedClock());
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000009001");

        assertThatThrownBy(
                        () ->
                                rateLimiter.requireAllowance(
                                        tenantId, "byok.test_connection", 10, Duration.ofHours(1)))
                .isInstanceOf(ByokRateLimiter.RateLimitExceededException.class)
                .satisfies(
                        throwable ->
                                assertThat(
                                                ((ByokRateLimiter.RateLimitExceededException)
                                                                throwable)
                                                        .errorCode())
                                        .isEqualTo("ai.byok.test_connection.rate_limited"));
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC);
    }
}
