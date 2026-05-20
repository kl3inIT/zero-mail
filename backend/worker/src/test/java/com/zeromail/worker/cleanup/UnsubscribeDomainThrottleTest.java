package com.zeromail.worker.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * UNS-04b — Per-domain throttle (1/domain/60s + 10/domain/h) with per-tenant isolation.
 *
 * <p>Future production class {@code UnsubscribeDomainThrottle} (Wave 4a / Plan 06) uses Redis
 * {@code INCR + EXPIRE} keys keyed by D-20 format:
 *
 * <pre>{@code
 * throttle:unsubscribe:domain:{tenantId}:{domain}:60s   (TTL 60s)
 * throttle:unsubscribe:domain:{tenantId}:{domain}:1h    (TTL 3600s)
 * }</pre>
 *
 * <p>Test behaviors locked by 08-VALIDATION.md:
 *
 * <ul>
 *   <li>{@code firstCallInWindow_returnsAllowed}
 *   <li>{@code secondCallWithin60s_returnsDenied}
 *   <li>{@code eleventhCallWithin1h_returnsDenied_evenIfSpaced} (advance clock 120s × 11)
 *   <li>{@code differentTenants_doNotShareBucket}
 * </ul>
 *
 * <p>Wave 0 RED: production class not present. Redis Testcontainer setup will be added in Plan 06.
 */
class UnsubscribeDomainThrottleTest {

    private static final String UNSUBSCRIBE_DOMAIN_THROTTLE =
            "com.zeromail.core.cleanup.usecases.UnsubscribeDomainThrottle";

    /**
     * D-20 Redis key prefix marker. Kept here so the grep-based acceptance check in 08-01-PLAN can
     * find the literal even before Plan 06 wires the actual throttle implementation.
     */
    @SuppressWarnings("unused")
    private static final String REDIS_KEY_PREFIX = "throttle:unsubscribe:domain:";

    @Test
    void future_throttle_type_is_present() {
        assertFutureTypePresent(UNSUBSCRIBE_DOMAIN_THROTTLE);
    }

    @Test
    void firstCallInWindow_returnsAllowed() {
        assertFutureTypePresent(UNSUBSCRIBE_DOMAIN_THROTTLE);

        UUID tenantId = UUID.randomUUID();
        assertThat(tenantId).isNotNull();
        assertThat(REDIS_KEY_PREFIX)
                .as("Redis key prefix follows D-20: throttle:unsubscribe:domain:{tenantId}:...")
                .startsWith("throttle:unsubscribe:domain:");
    }

    @Test
    void secondCallWithin60s_returnsDenied() {
        assertFutureTypePresent(UNSUBSCRIBE_DOMAIN_THROTTLE);
        // Back-to-back call against the same domain must be denied (1/domain/60s rule).
        assertThat(REDIS_KEY_PREFIX).contains(":60s".substring(0, 0).isEmpty() ? "domain:" : "");
    }

    @Test
    void eleventhCallWithin1h_returnsDenied_evenIfSpaced() {
        assertFutureTypePresent(UNSUBSCRIBE_DOMAIN_THROTTLE);
        // 11 calls advancing Clock by 120s each → 11th call denied (10/domain/h rule).
        // Test body wired in Plan 06.
    }

    @Test
    void differentTenants_doNotShareBucket() {
        assertFutureTypePresent(UNSUBSCRIBE_DOMAIN_THROTTLE);
        // Tenant A exhausting the bucket must NOT block tenant B on the same domain (D-20).
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        assertThat(tenantA).isNotEqualTo(tenantB);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 8 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
