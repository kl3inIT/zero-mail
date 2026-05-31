package com.zeromail.core.llm.byok;

import com.zeromail.core.llm.config.LlmProperties;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BaseUrlValidator {

    public static final String CODE_NOT_HTTPS = "ai.byok.base_url_not_https";
    public static final String CODE_HOST_PRIVATE = "ai.byok.base_url_host_private";
    public static final String CODE_HOST_UNRESOLVABLE = "ai.byok.base_url_host_unresolvable";
    public static final String CODE_PORT_NOT_ALLOWED = "ai.byok.base_url_port_not_allowed";

    private static final int MAX_BASE_URL_LENGTH = 255;
    private static final Set<Integer> DEV_EXTRA_PORTS = Set.of(8080, 11434);

    private final HostResolver hostResolver;
    private final Set<Integer> allowedExtraPorts;
    private final boolean devProfile;

    @Autowired
    public BaseUrlValidator(
            HostResolver hostResolver, LlmProperties llmProperties, Environment environment) {
        this(
                hostResolver,
                llmProperties.byok().allowedExtraPorts(),
                Arrays.asList(environment.getActiveProfiles()).contains("dev"));
    }

    public BaseUrlValidator(
            HostResolver hostResolver, Collection<Integer> allowedExtraPorts, boolean devProfile) {
        this.hostResolver = Objects.requireNonNull(hostResolver, "hostResolver");
        Set<Integer> configuredAllowedPorts = new HashSet<>();
        if (allowedExtraPorts != null) {
            configuredAllowedPorts.addAll(allowedExtraPorts);
        }
        if (devProfile) {
            configuredAllowedPorts.addAll(DEV_EXTRA_PORTS);
        }
        this.allowedExtraPorts = Set.copyOf(configuredAllowedPorts);
        this.devProfile = devProfile;
    }

    public ValidatedTarget validate(String baseUrl) {
        URI uri = parse(baseUrl);
        String scheme = uri.getScheme();
        String host = canonicalHost(uri.getHost());
        if (scheme == null || host.isBlank()) {
            throw invalid(CODE_NOT_HTTPS);
        }
        boolean devLocalHttp =
                devProfile && "http".equalsIgnoreCase(scheme) && isLocalhostName(host);
        if (!"https".equalsIgnoreCase(scheme) && !devLocalHttp) {
            throw invalid(CODE_NOT_HTTPS);
        }
        int effectivePort = effectivePort(uri);
        if (effectivePort != 443 && !allowedExtraPorts.contains(effectivePort)) {
            throw invalid(CODE_PORT_NOT_ALLOWED);
        }
        if (devLocalHttp) {
            return new ValidatedTarget(uri, InetAddress.getLoopbackAddress());
        }
        InetAddress[] resolvedAddresses = resolvePublicAddresses(host);
        return new ValidatedTarget(uri, resolvedAddresses[0]);
    }

    private URI parse(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank() || baseUrl.length() > MAX_BASE_URL_LENGTH) {
            throw invalid(CODE_NOT_HTTPS);
        }
        try {
            URI uri = new URI(baseUrl.trim());
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw invalid(CODE_NOT_HTTPS);
            }
            return uri;
        } catch (URISyntaxException uriSyntaxException) {
            throw invalid(CODE_NOT_HTTPS);
        }
    }

    private InetAddress[] resolvePublicAddresses(String host) {
        if (isIpLiteral(host)) {
            try {
                if (isBlockedAddress(InetAddress.getByName(stripIpv6Brackets(host)))) {
                    throw invalid(CODE_HOST_PRIVATE);
                }
            } catch (UnknownHostException ignored) {
                throw invalid(CODE_HOST_UNRESOLVABLE);
            }
        }
        InetAddress[] resolvedAddresses;
        try {
            resolvedAddresses = hostResolver.resolve(stripIpv6Brackets(host));
        } catch (UnknownHostException unknownHostException) {
            throw invalid(CODE_HOST_UNRESOLVABLE);
        }
        if (resolvedAddresses == null || resolvedAddresses.length == 0) {
            throw invalid(CODE_HOST_UNRESOLVABLE);
        }
        for (InetAddress resolvedAddress : resolvedAddresses) {
            if (isBlockedAddress(resolvedAddress)) {
                throw invalid(CODE_HOST_PRIVATE);
            }
        }
        return resolvedAddresses;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    public static boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isIpv6UniqueLocal(address)
                || isDocumentationOrBenchmarkIpv4(address);
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int firstOctet = Byte.toUnsignedInt(bytes[0]);
        int secondOctet = Byte.toUnsignedInt(bytes[1]);
        return firstOctet == 100 && secondOctet >= 64 && secondOctet <= 127;
    }

    private static boolean isIpv6UniqueLocal(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        return (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static boolean isDocumentationOrBenchmarkIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int firstOctet = Byte.toUnsignedInt(bytes[0]);
        int secondOctet = Byte.toUnsignedInt(bytes[1]);
        int thirdOctet = Byte.toUnsignedInt(bytes[2]);
        return (firstOctet == 192 && secondOctet == 0 && thirdOctet == 2)
                || (firstOctet == 198 && secondOctet == 51 && thirdOctet == 100)
                || (firstOctet == 203 && secondOctet == 0 && thirdOctet == 113)
                || (firstOctet == 198 && secondOctet >= 18 && secondOctet <= 19);
    }

    private static boolean isIpLiteral(String host) {
        String normalizedHost = stripIpv6Brackets(host);
        if (normalizedHost == null || normalizedHost.isBlank()) {
            return false;
        }
        if (normalizedHost.indexOf(':') >= 0) {
            return true;
        }
        return normalizedHost
                .chars()
                .allMatch(character -> Character.isDigit(character) || character == '.');
    }

    private static String canonicalHost(String host) {
        return host == null ? "" : stripIpv6Brackets(host).toLowerCase(Locale.ROOT);
    }

    private static String stripIpv6Brackets(String host) {
        if (host != null && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isLocalhostName(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static BaseUrlInvalidException invalid(String code) {
        return new BaseUrlInvalidException(code);
    }

    public record ValidatedTarget(URI uri, InetAddress resolvedAddress) {}

    public static class BaseUrlInvalidException extends BusinessException {

        private final String code;

        public BaseUrlInvalidException(String code) {
            super("BYOK base URL rejected");
            this.code = code;
        }

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return code;
        }

        @Override
        public String logEvent() {
            return "byok_base_url_rejected";
        }

        @Override
        public String title() {
            return "Invalid BYOK base URL";
        }

        @Override
        public String detail() {
            return "The BYOK base URL is not allowed.";
        }
    }
}
