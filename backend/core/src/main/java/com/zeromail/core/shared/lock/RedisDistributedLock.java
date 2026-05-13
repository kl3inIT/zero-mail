package com.zeromail.core.shared.lock;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisDistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    private final Supplier<StringRedisTemplate> stringRedisTemplateSupplier;
    private final Supplier<String> tokenSupplier;

    @Autowired
    public RedisDistributedLock(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this(stringRedisTemplateProvider::getIfAvailable, () -> UUID.randomUUID().toString());
    }

    public RedisDistributedLock(
            Supplier<StringRedisTemplate> stringRedisTemplateSupplier,
            Supplier<String> tokenSupplier) {
        this.stringRedisTemplateSupplier =
                Objects.requireNonNull(
                        stringRedisTemplateSupplier,
                        "stringRedisTemplateSupplier must not be null");
        this.tokenSupplier =
                Objects.requireNonNull(tokenSupplier, "tokenSupplier must not be null");
    }

    public Optional<LockHandle> tryAcquire(String key, Duration ttl) {
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateSupplier.get();
        if (stringRedisTemplate == null) {
            log.warn("event=redis_lock_unavailable keyPrefix={}", safeKeyPrefix(key));
            throw new LockBackendUnavailableException();
        }
        String lockKey = requireText(key, "key");
        Duration lockTtl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (lockTtl.isZero() || lockTtl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        String token = requireText(tokenSupplier.get(), "token");
        Boolean acquired;
        try {
            acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, token, lockTtl);
        } catch (RuntimeException redisFailure) {
            log.warn(
                    "event=redis_lock_unavailable keyPrefix={} reason={}",
                    safeKeyPrefix(key),
                    redisFailure.getClass().getSimpleName());
            throw new LockBackendUnavailableException(redisFailure);
        }
        if (!Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        return Optional.of(new RedisLockHandle(stringRedisTemplate, lockKey, token));
    }

    public interface LockHandle extends AutoCloseable {

        void release();

        @Override
        default void close() {
            release();
        }
    }

    private static final class RedisLockHandle implements LockHandle {

        private final StringRedisTemplate stringRedisTemplate;
        private final String key;
        private final String token;
        private final AtomicBoolean released = new AtomicBoolean();

        private RedisLockHandle(StringRedisTemplate stringRedisTemplate, String key, String token) {
            this.stringRedisTemplate =
                    Objects.requireNonNull(
                            stringRedisTemplate, "stringRedisTemplate must not be null");
            this.key = requireText(key, "key");
            this.token = requireText(token, "token");
        }

        @Override
        public void release() {
            if (released.getAndSet(true)) {
                return;
            }
            String currentToken = stringRedisTemplate.opsForValue().get(key);
            if (token.equals(currentToken)) {
                stringRedisTemplate.delete(key);
            }
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    private static String safeKeyPrefix(String key) {
        if (key == null || key.isBlank()) {
            return "blank";
        }
        int delimiterIndex = key.indexOf(':');
        return delimiterIndex < 0 ? key : key.substring(0, delimiterIndex);
    }
}
