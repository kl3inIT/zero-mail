package com.zeromail.core.admin.audit.usecases;

import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed debouncer for {@code admin_read_event} emission. Replaces the in-memory {@code
 * ConcurrentHashMap} debounce state previously held by AdminAuditController and
 * AdminSpendController — that state did not survive restart, did not share across api replicas, and
 * grew unbounded (no entry cleanup) creating a slow memory leak.
 *
 * <p>Implementation: Redis {@code SET NX EX} on a per-(admin, session, query) key. The first read
 * in a window claims the key and returns {@code true} (emit audit row); subsequent reads see the
 * existing key and return {@code false} (skip). The key expires automatically after the window — no
 * janitor needed.
 *
 * <p>If Redis is unavailable the debouncer fails open ({@code true} → audit row IS emitted).
 * Over-emission is preferable to silent under-emission of compliance-relevant read events.
 */
@Service
public class AdminReadEventDebouncer {

    private static final String KEY_PREFIX = "zeromail:admin:read-event-debounce:";

    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;

    public AdminReadEventDebouncer(
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.stringRedisTemplateProvider =
                Objects.requireNonNull(
                        stringRedisTemplateProvider,
                        "stringRedisTemplateProvider must not be null");
    }

    /**
     * Atomic claim: returns {@code true} when the caller should emit the audit row (debounce window
     * expired or never set), {@code false} when a previous emission is still within the window.
     * Redis outage fails open by returning {@code true}.
     */
    public boolean shouldEmit(String compositeKey, Duration debounceWindow) {
        Objects.requireNonNull(compositeKey, "compositeKey must not be null");
        Objects.requireNonNull(debounceWindow, "debounceWindow must not be null");
        StringRedisTemplate stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (stringRedisTemplate == null) {
            return true;
        }
        Boolean claimed =
                stringRedisTemplate
                        .opsForValue()
                        .setIfAbsent(KEY_PREFIX + compositeKey, "1", debounceWindow);
        return claimed == null || claimed;
    }
}
