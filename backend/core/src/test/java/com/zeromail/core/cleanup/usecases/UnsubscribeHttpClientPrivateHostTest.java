package com.zeromail.core.cleanup.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.cleanup.domain.UnsubscribeResult;
import com.zeromail.core.shared.net.OutboundHostGuard;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * CASA Tier 2 V5.2.6 — SSRF guard at the unsubscribe HTTP boundary.
 *
 * <p>When the resolved host is private (loopback, RFC 1918, cloud metadata, IPv6 ULA), the client
 * MUST short-circuit and return {@code Failed("BLOCKED_PRIVATE_HOST")} BEFORE any outbound HTTP
 * request is dispatched.
 */
class UnsubscribeHttpClientPrivateHostTest {

    @Test
    void privateHost_short_circuits_with_blocked_failure_reason() {
        OutboundHostGuard stubOutboundHostGuard =
                new OutboundHostGuard() {
                    @Override
                    public boolean isBlocked(String host) {
                        return "internal.test".equals(host);
                    }
                };
        UnsubscribeHttpClient unsubscribeHttpClient =
                new UnsubscribeHttpClient(stubOutboundHostGuard);

        UnsubscribeResult unsubscribeResult =
                unsubscribeHttpClient.postOneClick(
                        UUID.randomUUID(), "https://internal.test/unsub");

        assertThat(unsubscribeResult).isInstanceOf(UnsubscribeResult.Failed.class);
        assertThat(((UnsubscribeResult.Failed) unsubscribeResult).failureReason())
                .isEqualTo("BLOCKED_PRIVATE_HOST");
    }

    @Test
    void publicHost_passes_through_guard() {
        OutboundHostGuard stubOutboundHostGuard =
                new OutboundHostGuard() {
                    @Override
                    public boolean isBlocked(String host) {
                        return false;
                    }
                };
        UnsubscribeHttpClient unsubscribeHttpClient =
                new UnsubscribeHttpClient(stubOutboundHostGuard);

        // Public host with no listener on the URL — should reach the HTTP layer and fail with
        // NETWORK_ERROR/TIMEOUT, NOT BLOCKED_PRIVATE_HOST. This proves the guard is consulted
        // first and does not falsely block public hosts.
        UnsubscribeResult unsubscribeResult =
                unsubscribeHttpClient.postOneClick(
                        UUID.randomUUID(),
                        "https://this-host-definitely-does-not-exist.invalid/unsub");

        assertThat(unsubscribeResult).isInstanceOf(UnsubscribeResult.Failed.class);
        assertThat(((UnsubscribeResult.Failed) unsubscribeResult).failureReason())
                .isNotEqualTo("BLOCKED_PRIVATE_HOST");
    }
}
