package com.zeromail.core.llm.byok;

import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ByokRateLimiter {

    private static final String BYOK_TEST_CONNECTION_KEY = "byok.test_connection";
    private static final String VOICE_GENERATE_KEY = "voice.generate";

    private final Supplier<StringRedisTemplate> stringRedisTemplateSupplier;
    private final Clock clock;

    @Autowired
    public ByokRateLimiter(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this(stringRedisTemplateProvider::getIfAvailable, Clock.systemUTC());
    }

    public ByokRateLimiter(Supplier<StringRedisTemplate> stringRedisTemplateSupplier, Clock clock) {
        this.stringRedisTemplateSupplier =
                Objects.requireNonNull(
                        stringRedisTemplateSupplier,
                        "stringRedisTemplateSupplier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void requireAllowance(UUID tenantId, String key, int max, Duration window) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (max < 1) {
            throw new IllegalArgumentException("max must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        String redisKey = redisKey(tenantId, key, window);
        StringRedisTemplate stringRedisTemplate = stringRedisTemplate();
        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(redisKey, window);
            }
            if (count != null && count > max) {
                throw new RateLimitExceededException(errorCodeFor(key));
            }
        } catch (DataAccessException redisAccessException) {
            throw new RateLimitBackendUnavailableException(redisAccessException);
        }
    }

    private String redisKey(UUID tenantId, String key, Duration window) {
        long windowSeconds = window.toSeconds();
        long bucket = clock.instant().getEpochSecond() / windowSeconds;
        return "zeromail:" + key + ":" + tenantId + ":" + bucket;
    }

    private StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateSupplier.get();
        if (stringRedisTemplate == null) {
            throw new RateLimitBackendUnavailableException();
        }
        return stringRedisTemplate;
    }

    private static String errorCodeFor(String key) {
        if (VOICE_GENERATE_KEY.equals(key)) {
            return "voice.generate.rate_limited";
        }
        if (BYOK_TEST_CONNECTION_KEY.equals(key)) {
            return "ai.byok.test_connection.rate_limited";
        }
        return "ai.byok.rate_limited";
    }

    public static class RateLimitExceededException extends BusinessException {

        private final String errorCode;

        RateLimitExceededException(String errorCode) {
            super("BYOK rate limit exceeded");
            this.errorCode = errorCode;
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.TOO_MANY_REQUESTS;
        }

        @Override
        public String errorCode() {
            return errorCode;
        }

        @Override
        public String logEvent() {
            return "byok_rate_limited";
        }

        @Override
        public String title() {
            return "Too many requests";
        }

        @Override
        public String detail() {
            return "Too many BYOK operations were attempted in the current rate-limit window.";
        }
    }

    public static class RateLimitBackendUnavailableException extends BusinessException {

        public RateLimitBackendUnavailableException() {
            super("BYOK rate-limit backend is unavailable");
        }

        public RateLimitBackendUnavailableException(Throwable cause) {
            super("BYOK rate-limit backend is unavailable", cause);
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.SERVICE_UNAVAILABLE;
        }

        @Override
        public String errorCode() {
            return "ai.byok.rate_limit_unavailable";
        }

        @Override
        public String logEvent() {
            return "byok_rate_limit_backend_unavailable";
        }

        @Override
        public String title() {
            return "Rate limiter unavailable";
        }

        @Override
        public String detail() {
            return "Rate-limit backend is unavailable; BYOK operations are temporarily unavailable.";
        }
    }
}
