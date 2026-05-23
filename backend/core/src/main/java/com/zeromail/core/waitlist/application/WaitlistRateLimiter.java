package com.zeromail.core.waitlist.application;

import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed per-IP rate limiter for the public {@code POST /api/waitlist/subscribe} endpoint.
 *
 * <p>Bucket: 1 minute. Limit: {@value #LIMIT_PER_MINUTE} requests per source IP. Key shape: {@code
 * zeromail:waitlist:subscribe:<ipHash>:<epochMinute>}. The minute bucket lets the limiter stay
 * stateless across instances and self-expire without an explicit cleanup job.
 *
 * <p>The IP comes in as an already-hashed identifier so the limiter never touches the raw client
 * address (privacy). When Redis is unavailable the limiter throws {@link
 * RateLimitBackendUnavailableException} — callers translate that to HTTP 503 rather than fail-open
 * (cheap waitlist endpoint, abuse risk &gt; availability risk).
 */
@Component
public class WaitlistRateLimiter {

    static final int LIMIT_PER_MINUTE = 5;

    private final Supplier<StringRedisTemplate> stringRedisTemplateSupplier;
    private final Clock clock;

    @Autowired
    public WaitlistRateLimiter(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this(stringRedisTemplateProvider::getIfAvailable, Clock.systemUTC());
    }

    public WaitlistRateLimiter(
            Supplier<StringRedisTemplate> stringRedisTemplateSupplier, Clock clock) {
        this.stringRedisTemplateSupplier =
                Objects.requireNonNull(
                        stringRedisTemplateSupplier,
                        "stringRedisTemplateSupplier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void checkAllowed(String ipHash) {
        if (ipHash == null || ipHash.isBlank()) {
            return;
        }
        String redisKey = "zeromail:waitlist:subscribe:" + ipHash + ":" + epochMinute();
        StringRedisTemplate stringRedisTemplate = stringRedisTemplate();
        Long count = stringRedisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(redisKey, Duration.ofMinutes(1));
        }
        if (count != null && count > LIMIT_PER_MINUTE) {
            throw new WaitlistRateLimitExceededException();
        }
    }

    private long epochMinute() {
        return clock.instant().getEpochSecond() / 60L;
    }

    private StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateSupplier.get();
        if (stringRedisTemplate == null) {
            throw new RateLimitBackendUnavailableException();
        }
        return stringRedisTemplate;
    }

    public static class WaitlistRateLimitExceededException extends BusinessException {
        @Override
        public ErrorClass errorClass() {
            return ErrorClass.TOO_MANY_REQUESTS;
        }

        @Override
        public String errorCode() {
            return "error.waitlist.rate_limited";
        }

        @Override
        public String logEvent() {
            return "waitlist_rate_limited";
        }

        @Override
        public String title() {
            return "Too many requests";
        }

        @Override
        public String detail() {
            return "Too many waitlist subscribe attempts from this client; retry shortly.";
        }
    }

    public static class RateLimitBackendUnavailableException extends BusinessException {
        @Override
        public ErrorClass errorClass() {
            return ErrorClass.SERVICE_UNAVAILABLE;
        }

        @Override
        public String errorCode() {
            return "error.waitlist.rate_limit_unavailable";
        }

        @Override
        public String logEvent() {
            return "waitlist_rate_limit_backend_unavailable";
        }

        @Override
        public String title() {
            return "Rate limiter unavailable";
        }

        @Override
        public String detail() {
            return "Rate-limit backend is unavailable; the waitlist endpoint is temporarily refusing traffic.";
        }
    }
}
