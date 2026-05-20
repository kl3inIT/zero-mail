package com.zeromail.core.admin.mkey.usecases;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MasterKeyRateLimiter {

    private static final int EDIT_LIMIT_PER_HOUR = 10;
    private static final int TEST_CONNECTION_LIMIT_PER_HOUR = 30;

    private final Supplier<StringRedisTemplate> stringRedisTemplateSupplier;
    private final Clock clock;

    @Autowired
    public MasterKeyRateLimiter(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this(stringRedisTemplateProvider::getIfAvailable, Clock.systemUTC());
    }

    public MasterKeyRateLimiter(
            Supplier<StringRedisTemplate> stringRedisTemplateSupplier, Clock clock) {
        this.stringRedisTemplateSupplier =
                Objects.requireNonNull(
                        stringRedisTemplateSupplier,
                        "stringRedisTemplateSupplier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void checkEditAllowed(UUID actorId) {
        checkAllowed("zeromail:mkey:edits:" + actorId + ":" + epochHour(), EDIT_LIMIT_PER_HOUR);
    }

    public void checkTestConnectionAllowed(UUID actorId) {
        checkAllowed(
                "zeromail:mkey:test:" + actorId + ":" + epochHour(),
                TEST_CONNECTION_LIMIT_PER_HOUR);
    }

    private void checkAllowed(String redisKey, int limit) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplate();
        Long count = stringRedisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(redisKey, Duration.ofHours(1));
        }
        if (count != null && count > limit) {
            throw new RateLimitExceededException();
        }
    }

    private long epochHour() {
        return clock.instant().getEpochSecond() / 3600L;
    }

    private StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateSupplier.get();
        if (stringRedisTemplate == null) {
            throw new RateLimitBackendUnavailableException();
        }
        return stringRedisTemplate;
    }

    public static class RateLimitExceededException extends AdminBusinessException {
        @Override
        public ErrorClass errorClass() {
            return ErrorClass.TOO_MANY_REQUESTS;
        }

        @Override
        public String errorCode() {
            return "error.admin.master_key_rate_limited";
        }

        @Override
        public String logEvent() {
            return "admin_master_key_rate_limited";
        }

        @Override
        public String detail() {
            return "Too many master-key requests; retry after the cooldown window.";
        }
    }

    public static class RateLimitBackendUnavailableException extends RuntimeException {}
}
