package com.zeromail.core.waitlist.application;

import static org.assertj.core.api.Assertions.assertThatCode;
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
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class WaitlistRateLimiterTest {

    private static final String IP_HASH = "abc123";

    @Test
    void first_request_sets_one_minute_ttl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(1L);
        WaitlistRateLimiter rateLimiter =
                new WaitlistRateLimiter(() -> redisTemplate, fixedClock());

        rateLimiter.checkAllowed(IP_HASH);

        long epochMinute = fixedClock().instant().getEpochSecond() / 60L;
        verify(redisTemplate)
                .expire(
                        eq("zeromail:waitlist:subscribe:" + IP_HASH + ":" + epochMinute),
                        eq(Duration.ofMinutes(1)));
    }

    @Test
    void sixth_request_in_same_minute_throws_rate_limit_exceeded() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(6L);
        WaitlistRateLimiter rateLimiter =
                new WaitlistRateLimiter(() -> redisTemplate, fixedClock());

        assertThatThrownBy(() -> rateLimiter.checkAllowed(IP_HASH))
                .isInstanceOf(WaitlistRateLimiter.WaitlistRateLimitExceededException.class);
    }

    @Test
    void null_ip_hash_short_circuits_without_touching_redis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        WaitlistRateLimiter rateLimiter =
                new WaitlistRateLimiter(() -> redisTemplate, fixedClock());

        assertThatCode(() -> rateLimiter.checkAllowed(null)).doesNotThrowAnyException();
        verify(redisTemplate, org.mockito.Mockito.never()).opsForValue();
    }

    @Test
    void redis_unavailable_throws_backend_unavailable_exception() {
        WaitlistRateLimiter rateLimiter = new WaitlistRateLimiter(() -> null, fixedClock());

        assertThatThrownBy(() -> rateLimiter.checkAllowed(IP_HASH))
                .isInstanceOf(WaitlistRateLimiter.RateLimitBackendUnavailableException.class);
    }

    @Test
    void redis_access_failure_throws_backend_unavailable_exception() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any()))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        WaitlistRateLimiter rateLimiter =
                new WaitlistRateLimiter(() -> redisTemplate, fixedClock());

        assertThatThrownBy(() -> rateLimiter.checkAllowed(IP_HASH))
                .isInstanceOf(WaitlistRateLimiter.RateLimitBackendUnavailableException.class);
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneOffset.UTC);
    }
}
