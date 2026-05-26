package com.zeromail.core.shared.net;

/**
 * Thrown when an outbound HTTP host resolves to a blocked network range (private RFC 1918,
 * loopback, link-local, cloud metadata, IPv6 ULA) or when DNS resolution fails (fail-closed).
 *
 * <p>Used by SSRF guards (BYOK endpoint validation, RFC 8058 one-click unsubscribe, future outbound
 * HTTP clients) to halt any request that would target operator-internal infrastructure.
 */
public class BlockedOutboundHostException extends RuntimeException {

    public BlockedOutboundHostException(String message) {
        super(message);
    }
}
