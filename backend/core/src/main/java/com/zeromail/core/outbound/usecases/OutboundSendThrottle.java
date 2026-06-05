package com.zeromail.core.outbound.usecases;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed per-tenant rate cap on rule-triggered outbound auto-sends (send_reply /
 * forward_email / send_email).
 *
 * <p>This is the defense-in-depth backstop behind the {@code SENT}-label loop guard in {@code
 * TriageOrchestratorService}. The loop guard prevents the common self-send feedback loop at the
 * source; this throttle bounds the blast radius of ANY future runaway (a new ingestion path, a
 * misconfigured rule fanning out, an LLM that proposes many sends) so a single tenant can never
 * flood Gmail or burn unbounded credits before a human notices.
 *
 * <p>Two-bucket sliding window, per tenant:
 *
 * <ul>
 *   <li>Short bucket: at most {@value #MAX_PER_60S} auto-sends per 60 seconds.
 *   <li>Long bucket: at most {@value #MAX_PER_1H} auto-sends per hour.
 * </ul>
 *
 * <p>Key format:
 *
 * <pre>{@code
 * throttle:outbound:send:{tenantId}:60s   (TTL 60s)
 * throttle:outbound:send:{tenantId}:1h    (TTL 3600s)
 * }</pre>
 *
 * <p>Per-tenant scope keeps a noisy tenant from starving others. On deny the caller downgrades the
 * outbound action to a Gmail draft (same fallback mechanism as the Auto-send toggle and the sender
 * safety-net), so no mail is lost — the user still sees the would-be send as a reviewable draft.
 *
 * <p>Privacy invariant: log lines carry only {@code tenantId} + window + count — never recipient,
 * subject, or any user content.
 */
@Component
public class OutboundSendThrottle {

    static final int MAX_PER_60S = 15;
    static final int MAX_PER_1H = 120;
    private static final Duration WINDOW_60S = Duration.ofSeconds(60);
    private static final Duration WINDOW_1H = Duration.ofHours(1);

    private static final Logger log = LoggerFactory.getLogger(OutboundSendThrottle.class);
    private static final String KEY_60S = "throttle:outbound:send:%s:60s";
    private static final String KEY_1H = "throttle:outbound:send:%s:1h";

    private final StringRedisTemplate stringRedisTemplate;

    public OutboundSendThrottle(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate =
                Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate must not be null");
    }

    /**
     * Try to acquire an auto-send slot for the given tenant.
     *
     * <p>Increments both the 60-second and the 1-hour buckets. Returns {@code true} iff both
     * post-increment counts are still within their caps. On deny, decrements both buckets so a
     * denied attempt does not permanently consume budget.
     *
     * @return {@code true} if the auto-send may proceed, {@code false} if the cap is exceeded.
     */
    public boolean acquire(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        String key60s = KEY_60S.formatted(tenantId);
        String key1h = KEY_1H.formatted(tenantId);

        Long count60s = stringRedisTemplate.opsForValue().increment(key60s);
        if (count60s != null && count60s == 1L) {
            stringRedisTemplate.expire(key60s, WINDOW_60S);
        }
        Long count1h = stringRedisTemplate.opsForValue().increment(key1h);
        if (count1h != null && count1h == 1L) {
            stringRedisTemplate.expire(key1h, WINDOW_1H);
        }

        if (count60s != null && count60s > MAX_PER_60S) {
            log.warn(
                    "event=outbound_send_throttled tenantId={} window=60s count={}",
                    tenantId,
                    count60s);
            rollback(key60s, key1h);
            return false;
        }
        if (count1h != null && count1h > MAX_PER_1H) {
            log.warn(
                    "event=outbound_send_throttled tenantId={} window=1h count={}",
                    tenantId,
                    count1h);
            rollback(key60s, key1h);
            return false;
        }
        return true;
    }

    private void rollback(String key60s, String key1h) {
        stringRedisTemplate.opsForValue().decrement(key60s);
        stringRedisTemplate.opsForValue().decrement(key1h);
    }
}
