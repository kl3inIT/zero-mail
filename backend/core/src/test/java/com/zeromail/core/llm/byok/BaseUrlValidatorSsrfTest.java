package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BaseUrlValidatorSsrfTest {

    @ParameterizedTest
    @CsvSource({
        "http://10.0.0.1, ai.byok.base_url_not_https",
        "https://192.168.1.1, ai.byok.base_url_host_private",
        "https://169.254.169.254, ai.byok.base_url_host_private",
        "https://127.0.0.1, ai.byok.base_url_host_private",
        "https://localhost.evil.tld, ai.byok.base_url_host_private",
        "https://[::1], ai.byok.base_url_host_private",
        "https://[fc00::1], ai.byok.base_url_host_private",
        "https://api.openai.com:8443/v1, ai.byok.base_url_port_not_allowed"
    })
    void rejects_ssrf_and_policy_bypass_urls(String baseUrl, String expectedCode) {
        BaseUrlValidator validator = validator(false, List.of(), hostileAndProviderResolutions());

        assertThatThrownBy(() -> validator.validate(baseUrl))
                .isInstanceOf(BaseUrlValidator.BaseUrlInvalidException.class)
                .satisfies(
                        throwable ->
                                assertThat(
                                                ((BaseUrlValidator.BaseUrlInvalidException)
                                                                throwable)
                                                        .errorCode())
                                        .isEqualTo(expectedCode));
    }

    @ParameterizedTest
    @CsvSource({
        "https://api.openai.com/v1",
        "https://api.anthropic.com/v1",
        "https://generativelanguage.googleapis.com/v1beta",
        "https://api.deepseek.com/v1"
    })
    void accepts_canonical_provider_urls(String baseUrl) {
        BaseUrlValidator validator = validator(false, List.of(), hostileAndProviderResolutions());

        assertThatCode(() -> validator.validate(baseUrl)).doesNotThrowAnyException();
    }

    @Test
    void allows_http_localhost_only_in_dev_profile_on_allowed_dev_port() {
        BaseUrlValidator validator = validator(true, List.of(), Map.of());

        BaseUrlValidator.ValidatedTarget target = validator.validate("http://localhost:11434");

        assertThat(target.uri().toString()).isEqualTo("http://localhost:11434");
        assertThat(target.resolvedAddress().isLoopbackAddress()).isTrue();
    }

    private static BaseUrlValidator validator(
            boolean devProfile, List<Integer> extraPorts, Map<String, InetAddress[]> resolutions) {
        return new BaseUrlValidator(new StubHostResolver(resolutions), extraPorts, devProfile);
    }

    private static Map<String, InetAddress[]> hostileAndProviderResolutions() {
        return Map.of(
                "localhost.evil.tld", addresses("127.0.0.1"),
                "api.openai.com", addresses("8.8.8.8"),
                "api.anthropic.com", addresses("8.8.4.4"),
                "generativelanguage.googleapis.com", addresses("1.1.1.1"),
                "api.deepseek.com", addresses("1.0.0.1"));
    }

    private static InetAddress[] addresses(String... values) {
        try {
            InetAddress[] addresses = new InetAddress[values.length];
            for (int index = 0; index < values.length; index++) {
                addresses[index] = InetAddress.getByName(values[index]);
            }
            return addresses;
        } catch (UnknownHostException unknownHostException) {
            throw new IllegalStateException(unknownHostException);
        }
    }

    private static class StubHostResolver extends HostResolver {

        private final Map<String, InetAddress[]> resolutions;

        StubHostResolver(Map<String, InetAddress[]> resolutions) {
            this.resolutions = resolutions;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = resolutions.get(host);
            if (addresses == null) {
                throw new UnknownHostException(host);
            }
            return addresses;
        }
    }
}
