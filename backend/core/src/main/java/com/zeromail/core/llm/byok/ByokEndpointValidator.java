package com.zeromail.core.llm.byok;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmByokProperties;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.exception.InvalidByokException;
import com.zeromail.core.tenant.TenantContext;

/**
 * SSRF guard for user-controlled BYOK endpoints.
 *
 * <p>Blocked network ranges include:
 *
 * <ul>
 *   <li>10.0.0.0/8
 *   <li>172.16.0.0/12
 *   <li>192.168.0.0/16
 *   <li>169.254.0.0/16, including the metadata address 169.254.169.254
 *   <li>127.0.0.0/8 and ::1
 * </ul>
 */
@Component
public class ByokEndpointValidator {

  private static final Logger log = LoggerFactory.getLogger(ByokEndpointValidator.class);

  private static final String ANTHROPIC_DEFAULT_ENDPOINT = "https://api.anthropic.com/v1";
  private static final String DEEPSEEK_DEFAULT_ENDPOINT = "https://api.deepseek.com";
  private static final String GOOGLE_GENAI_DEFAULT_ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta";
  private static final String OPENAI_DEFAULT_ENDPOINT = "https://api.openai.com/v1";
  private static final String METADATA_IPV4_ADDRESS = "169.254.169.254";

  private final ZeroMailLlmByokProperties byokProperties;
  private final Set<String> allowedExtraHosts;

  @Autowired
  public ByokEndpointValidator(ZeroMailCoreProperties zeroMailCoreProperties) {
    this(zeroMailCoreProperties.llm().byok());
  }

  public ByokEndpointValidator(ZeroMailLlmByokProperties byokProperties) {
    this.byokProperties = byokProperties;
    this.allowedExtraHosts =
        byokProperties.allowedExtraHosts().stream()
            .map(ByokEndpointValidator::canonicalHost)
            .collect(Collectors.toUnmodifiableSet());
  }

  public String validateAnthropic(String endpoint) {
    String canonicalEndpoint = canonicalEndpoint(endpoint, ANTHROPIC_DEFAULT_ENDPOINT);
    URI endpointUri = parseAndValidateUri(canonicalEndpoint, BYOKProvider.ANTHROPIC);
    String host = canonicalHost(endpointUri.getHost());
    if (!isAnthropicHost(host)) {
      throw rejected(BYOKProvider.ANTHROPIC);
    }
    rejectPrivateResolvedAddresses(host, BYOKProvider.ANTHROPIC);
    return canonicalEndpoint;
  }

  public String validateOpenAiCompatible(String endpoint) {
    return validateOpenAi(endpoint);
  }

  public String validateOpenAi(String endpoint) {
    String canonicalEndpoint = canonicalEndpoint(endpoint, OPENAI_DEFAULT_ENDPOINT);
    return validateOpenAiEndpoint(canonicalEndpoint, false);
  }

  public String validateCustomOpenAiCompatible(String endpoint) {
    String canonicalEndpoint = requiredCanonicalEndpoint(endpoint, BYOKProvider.OPENAI);
    return validateOpenAiEndpoint(canonicalEndpoint, true);
  }

  public String validateAnthropicCompatible(String endpoint) {
    String canonicalEndpoint = requiredCanonicalEndpoint(endpoint, BYOKProvider.ANTHROPIC);
    URI endpointUri = parseAndValidateUri(canonicalEndpoint, BYOKProvider.ANTHROPIC);
    String host = canonicalHost(endpointUri.getHost());
    if (!isAnthropicHost(host) && !isOperatorAllowedNonVendorHost(host)) {
      throw rejected(BYOKProvider.ANTHROPIC);
    }
    rejectPrivateResolvedAddresses(host, BYOKProvider.ANTHROPIC);
    return canonicalEndpoint;
  }

  public String validateGoogleGenAi(String endpoint) {
    String canonicalEndpoint = canonicalEndpoint(endpoint, GOOGLE_GENAI_DEFAULT_ENDPOINT);
    URI endpointUri = parseAndValidateUri(canonicalEndpoint, BYOKProvider.GOOGLE_GENAI);
    String host = canonicalHost(endpointUri.getHost());
    if (!isGoogleGenAiHost(host)) {
      throw rejected(BYOKProvider.GOOGLE_GENAI);
    }
    rejectPrivateResolvedAddresses(host, BYOKProvider.GOOGLE_GENAI);
    return canonicalEndpoint;
  }

  public String validateDeepSeek(String endpoint) {
    String canonicalEndpoint = canonicalEndpoint(endpoint, DEEPSEEK_DEFAULT_ENDPOINT);
    URI endpointUri = parseAndValidateUri(canonicalEndpoint, BYOKProvider.DEEPSEEK);
    String host = canonicalHost(endpointUri.getHost());
    if (!isDeepSeekHost(host)) {
      throw rejected(BYOKProvider.DEEPSEEK);
    }
    rejectPrivateResolvedAddresses(host, BYOKProvider.DEEPSEEK);
    return canonicalEndpoint;
  }

  private String validateOpenAiEndpoint(String canonicalEndpoint, boolean customEndpoint) {
    URI endpointUri = parseAndValidateUri(canonicalEndpoint, BYOKProvider.OPENAI);
    String host = canonicalHost(endpointUri.getHost());
    if (!isOpenAiVendorHost(host) && !(customEndpoint && isOperatorAllowedNonVendorHost(host))) {
      throw rejected(BYOKProvider.OPENAI);
    }
    rejectPrivateResolvedAddresses(host, BYOKProvider.OPENAI);
    return canonicalEndpoint;
  }

  private boolean isOperatorAllowedNonVendorHost(String host) {
    return byokProperties.allowNonVendorEndpoints() && allowedExtraHosts.contains(host);
  }

  private URI parseAndValidateUri(String canonicalEndpoint, BYOKProvider provider) {
    URI endpointUri;
    try {
      endpointUri = URI.create(canonicalEndpoint);
    } catch (IllegalArgumentException invalidUriFailure) {
      throw rejected(provider);
    }
    if (!"https".equalsIgnoreCase(endpointUri.getScheme())
        || endpointUri.getHost() == null
        || endpointUri.getUserInfo() != null
        || endpointUri.getQuery() != null
        || endpointUri.getFragment() != null) {
      throw rejected(provider);
    }
    return endpointUri;
  }

  private static String canonicalEndpoint(String endpoint, String defaultEndpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      return defaultEndpoint;
    }
    return endpoint.trim().replaceAll("/+$", "");
  }

  private String requiredCanonicalEndpoint(String endpoint, BYOKProvider provider) {
    if (endpoint == null || endpoint.isBlank()) {
      throw rejected(provider);
    }
    return endpoint.trim().replaceAll("/+$", "");
  }

  private void rejectPrivateResolvedAddresses(String host, BYOKProvider provider) {
    InetAddress[] resolvedAddresses;
    try {
      resolvedAddresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException dnsFailure) {
      throw rejected(provider);
    }
    for (InetAddress resolvedAddress : resolvedAddresses) {
      if (isBlockedAddress(resolvedAddress)) {
        throw rejected(provider);
      }
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

  private static boolean isAnthropicHost(String host) {
    return host.equals("anthropic.com")
        || host.equals("api.anthropic.com")
        || host.endsWith(".anthropic.com");
  }

  private static boolean isOpenAiVendorHost(String host) {
    return host.equals("openai.com")
        || host.endsWith(".openai.com")
        || host.equals("openrouter.ai")
        || host.endsWith(".openrouter.ai");
  }

  private static boolean isGoogleGenAiHost(String host) {
    return host.equals("generativelanguage.googleapis.com");
  }

  private static boolean isDeepSeekHost(String host) {
    return host.equals("deepseek.com")
        || host.equals("api.deepseek.com")
        || host.endsWith(".deepseek.com");
  }

  private static String canonicalHost(String host) {
    return host == null ? "" : host.toLowerCase(Locale.ROOT);
  }

  private InvalidByokException rejected(BYOKProvider provider) {
    log.warn(
        "event=byok_validate_failed tenantId={} provider={} reason=endpoint_rejected",
        tenantIdForLog(),
        provider);
    return new InvalidByokException();
  }

  private String tenantIdForLog() {
    try {
      return TenantContext.currentOrThrow();
    } catch (RuntimeException tenantContextMissing) {
      return "unknown";
    }
  }
}
