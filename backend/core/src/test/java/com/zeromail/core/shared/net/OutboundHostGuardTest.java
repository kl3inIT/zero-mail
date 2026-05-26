package com.zeromail.core.shared.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboundHostGuardTest {

    private final OutboundHostGuard outboundHostGuard = new OutboundHostGuard();

    @Test
    void blocks_localhost_loopback() {
        assertThat(outboundHostGuard.isBlocked("localhost")).isTrue();
    }

    @Test
    void blocks_ipv4_loopback_literal() {
        assertThat(outboundHostGuard.isBlocked("127.0.0.1")).isTrue();
    }

    @Test
    void blocks_rfc1918_class_a() {
        assertThat(outboundHostGuard.isBlocked("10.0.0.1")).isTrue();
    }

    @Test
    void blocks_rfc1918_class_c() {
        assertThat(outboundHostGuard.isBlocked("192.168.1.1")).isTrue();
    }

    @Test
    void blocks_rfc1918_class_b() {
        assertThat(outboundHostGuard.isBlocked("172.16.0.1")).isTrue();
    }

    @Test
    void blocks_cloud_metadata_address() {
        assertThat(outboundHostGuard.isBlocked("169.254.169.254")).isTrue();
    }

    @Test
    void blocks_link_local_ipv4() {
        assertThat(outboundHostGuard.isBlocked("169.254.1.1")).isTrue();
    }

    @Test
    void blocks_ipv6_loopback() {
        assertThat(outboundHostGuard.isBlocked("::1")).isTrue();
    }

    @Test
    void blocks_ipv6_unique_local_fc00() {
        assertThat(outboundHostGuard.isBlocked("fc00::1")).isTrue();
    }

    @Test
    void blocks_ipv6_unique_local_fd00() {
        assertThat(outboundHostGuard.isBlocked("fd00::1")).isTrue();
    }

    @Test
    void allows_public_dns_8888() {
        assertThat(outboundHostGuard.isBlocked("8.8.8.8")).isFalse();
    }

    @Test
    void allows_public_dns_1111() {
        assertThat(outboundHostGuard.isBlocked("1.1.1.1")).isFalse();
    }

    @Test
    void require_allowed_throws_on_unknown_host() {
        assertThatThrownBy(
                        () ->
                                outboundHostGuard.requireAllowed(
                                        "this-host-definitely-does-not-exist.invalid"))
                .isInstanceOf(BlockedOutboundHostException.class);
    }
}
