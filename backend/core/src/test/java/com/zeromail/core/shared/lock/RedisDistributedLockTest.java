package com.zeromail.core.shared.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisDistributedLockTest {

    @Test
    void try_acquire_throws_when_backend_is_unavailable() {
        RedisDistributedLock redisDistributedLock =
                new RedisDistributedLock(() -> null, () -> "token");

        assertThatThrownBy(
                        () ->
                                redisDistributedLock.tryAcquire(
                                        "draft:lock:tenant:thread", Duration.ofSeconds(60)))
                .isInstanceOf(LockBackendUnavailableException.class);
    }

    @Test
    void try_acquire_returns_empty_when_key_is_held() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = valueOperations(stringRedisTemplate);
        when(valueOperations.setIfAbsent(
                        "draft:lock:tenant:thread", "token", Duration.ofSeconds(60)))
                .thenReturn(false);
        RedisDistributedLock redisDistributedLock =
                new RedisDistributedLock(() -> stringRedisTemplate, () -> "token");

        Optional<RedisDistributedLock.LockHandle> lockHandle =
                redisDistributedLock.tryAcquire("draft:lock:tenant:thread", Duration.ofSeconds(60));

        assertThat(lockHandle).isEmpty();
    }

    @Test
    void release_deletes_only_when_token_matches() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = valueOperations(stringRedisTemplate);
        when(valueOperations.setIfAbsent(
                        "draft:lock:tenant:thread", "token", Duration.ofSeconds(60)))
                .thenReturn(true);
        when(valueOperations.get("draft:lock:tenant:thread")).thenReturn("different-token");
        RedisDistributedLock redisDistributedLock =
                new RedisDistributedLock(() -> stringRedisTemplate, () -> "token");

        RedisDistributedLock.LockHandle lockHandle =
                redisDistributedLock
                        .tryAcquire("draft:lock:tenant:thread", Duration.ofSeconds(60))
                        .orElseThrow();

        lockHandle.release();

        verify(stringRedisTemplate, never()).delete("draft:lock:tenant:thread");
    }

    @Test
    void release_deletes_matching_token_once() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = valueOperations(stringRedisTemplate);
        when(valueOperations.setIfAbsent(
                        "draft:lock:tenant:thread", "token", Duration.ofSeconds(60)))
                .thenReturn(true);
        when(valueOperations.get("draft:lock:tenant:thread")).thenReturn("token");
        RedisDistributedLock redisDistributedLock =
                new RedisDistributedLock(() -> stringRedisTemplate, () -> "token");

        RedisDistributedLock.LockHandle lockHandle =
                redisDistributedLock
                        .tryAcquire("draft:lock:tenant:thread", Duration.ofSeconds(60))
                        .orElseThrow();

        lockHandle.release();
        lockHandle.release();

        verify(stringRedisTemplate).delete("draft:lock:tenant:thread");
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> valueOperations(
            StringRedisTemplate stringRedisTemplate) {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        return valueOperations;
    }
}
