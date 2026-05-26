package com.zeromail.core.shared.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/**
 * Centralised SSRF guard for outbound HTTP hosts. Rejects hosts that resolve to private RFC 1918
 * ranges, loopback, link-local, the cloud metadata service ({@code 169.254.169.254}), or IPv6
 * unique-local addresses ({@code fc00::/7}). DNS failures are treated as blocked (fail-closed).
 *
 * <p>Shared by:
 *
 * <ul>
 *   <li>{@code com.zeromail.core.llm.byok.ByokEndpointValidator} — user-supplied BYOK base URLs.
 *   <li>{@code com.zeromail.core.cleanup.usecases.UnsubscribeHttpClient} — RFC 8058 one-click
 *       unsubscribe POST.
 * </ul>
 *
 * <p>Closes CASA Tier 2 V5.2.6 (SSRF).
 */
@Component
public class OutboundHostGuard {

    private static final String METADATA_IPV4_ADDRESS = "169.254.169.254";

    /**
     * @return {@code true} if {@code host} is null/blank, fails DNS resolution, or resolves to any
     *     address in a blocked range; {@code false} only when every resolved address is public.
     */
    public boolean isBlocked(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        InetAddress[] resolvedAddresses;
        try {
            resolvedAddresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException dnsFailure) {
            return true;
        }
        for (InetAddress resolvedAddress : resolvedAddresses) {
            if (isBlockedAddress(resolvedAddress)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience wrapper that throws {@link BlockedOutboundHostException} when {@link
     * #isBlocked(String)} returns {@code true}.
     */
    public void requireAllowed(String host) {
        if (isBlocked(host)) {
            throw new BlockedOutboundHostException("Outbound host is blocked or unresolvable");
        }
    }

    private static boolean isBlockedAddress(InetAddress resolvedAddress) {
        return resolvedAddress.isAnyLocalAddress()
                || resolvedAddress.isLoopbackAddress()
                || resolvedAddress.isLinkLocalAddress()
                || resolvedAddress.isSiteLocalAddress()
                || isMetadataAddress(resolvedAddress)
                || isIpv6UniqueLocalAddress(resolvedAddress);
    }

    private static boolean isMetadataAddress(InetAddress resolvedAddress) {
        return resolvedAddress instanceof Inet4Address
                && METADATA_IPV4_ADDRESS.equals(resolvedAddress.getHostAddress());
    }

    private static boolean isIpv6UniqueLocalAddress(InetAddress resolvedAddress) {
        if (!(resolvedAddress instanceof Inet6Address)) {
            return false;
        }
        byte firstByte = resolvedAddress.getAddress()[0];
        return (firstByte & 0xfe) == 0xfc;
    }
}
