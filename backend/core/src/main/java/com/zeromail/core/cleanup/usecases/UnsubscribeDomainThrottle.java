package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed per-tenant per-domain unsubscribe throttle (D-20).
 *
 * <p>Two-bucket sliding window:
 *
 * <ul>
 *   <li>Short bucket: at most {@value UnsubscribeCampaignPolicy#THROTTLE_MAX_PER_DOMAIN_60S}
 *       unsubscribe step per domain per 60 seconds.
 *   <li>Long bucket: at most {@value UnsubscribeCampaignPolicy#THROTTLE_MAX_PER_DOMAIN_1H}
 *       unsubscribe steps per domain per hour.
 * </ul>
 *
 * <p>Key format (D-20 strict — referenced by {@code UnsubscribeDomainThrottleTest}):
 *
 * <pre>{@code
 * throttle:unsubscribe:domain:{tenantId}:{domain}:60s   (TTL 60s)
 * throttle:unsubscribe:domain:{tenantId}:{domain}:1h    (TTL 3600s)
 * }</pre>
 *
 * <p>Per-tenant scope ensures tenant A exhausting its bucket cannot block tenant B on the same
 * domain (T-08-06 noisy-neighbor mitigation).
 *
 * <p>Privacy invariant (UNS-09): log lines only carry {@code tenantId} + {@code senderDomain} —
 * never the full sender email or any user content.
 */
@Component
public class UnsubscribeDomainThrottle {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeDomainThrottle.class);
    private static final String KEY_60S = "throttle:unsubscribe:domain:%s:%s:60s";
    private static final String KEY_1H = "throttle:unsubscribe:domain:%s:%s:1h";

    private final StringRedisTemplate stringRedisTemplate;

    public UnsubscribeDomainThrottle(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate =
                Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate must not be null");
    }

    /**
     * Try to acquire a throttle slot for the given tenant + sender domain pair.
     *
     * <p>Increments both the 60-second and the 1-hour buckets. Returns {@code true} iff both
     * post-increment counts are still within their respective caps. On deny, decrements both
     * buckets so a denied attempt does not permanently consume budget.
     *
     * @return {@code true} if the attempt may proceed, {@code false} if the throttle is exceeded.
     */
    public boolean acquire(UUID tenantId, String senderDomain) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(senderDomain, "senderDomain must not be null");
        if (senderDomain.isBlank()) {
            throw new IllegalArgumentException("senderDomain must not be blank");
        }

        String key60s = KEY_60S.formatted(tenantId, senderDomain);
        String key1h = KEY_1H.formatted(tenantId, senderDomain);

        Long count60s = stringRedisTemplate.opsForValue().increment(key60s);
        if (count60s != null && count60s == 1L) {
            stringRedisTemplate.expire(
                    key60s, UnsubscribeCampaignPolicy.THROTTLE_PER_DOMAIN_WINDOW_60S);
        }
        Long count1h = stringRedisTemplate.opsForValue().increment(key1h);
        if (count1h != null && count1h == 1L) {
            stringRedisTemplate.expire(
                    key1h, UnsubscribeCampaignPolicy.THROTTLE_PER_DOMAIN_WINDOW_1H);
        }

        if (count60s != null && count60s > UnsubscribeCampaignPolicy.THROTTLE_MAX_PER_DOMAIN_60S) {
            log.info(
                    "event=cleanup_throttle_deferred tenantId={} senderDomain={} window=60s count={}",
                    tenantId,
                    senderDomain,
                    count60s);
            rollback(key60s, key1h);
            return false;
        }
        if (count1h != null && count1h > UnsubscribeCampaignPolicy.THROTTLE_MAX_PER_DOMAIN_1H) {
            log.info(
                    "event=cleanup_throttle_deferred tenantId={} senderDomain={} window=1h count={}",
                    tenantId,
                    senderDomain,
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
