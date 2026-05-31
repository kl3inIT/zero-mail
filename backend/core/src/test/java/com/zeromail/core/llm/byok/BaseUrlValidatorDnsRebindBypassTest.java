package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.zeromail.core.llm.config.LlmProperties.ByokProperties;
import com.zeromail.core.llm.domain.LlmProvider;
import com.zeromail.core.llm.domain.MasterKeyTestResult;
import com.zeromail.core.llm.gateway.springai.ConnectionTestResult;
import com.zeromail.core.llm.gateway.springai.ModelsProbeClient;
import com.zeromail.core.llm.gateway.springai.ProviderConnectionTester;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class BaseUrlValidatorDnsRebindBypassTest {

    @Test
    void provider_probe_uses_validated_pinned_address_for_unresolvable_request_host()
            throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<InetAddress> observedPinnedAddress = new AtomicReference<>();
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/models",
                exchange -> {
                    requestCount.incrementAndGet();
                    byte[] body =
                            "{\"data\":[{\"id\":\"gpt-4o-mini\"}]}"
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        try {
            int port = server.getAddress().getPort();
            InetAddress validatedAddress = InetAddress.getLoopbackAddress();
            PinnedHttpClientFactory pinnedHttpClientFactory =
                    new PinnedHttpClientFactory(
                            new ByokProperties(
                                    false,
                                    List.of(),
                                    List.of(),
                                    Duration.ofSeconds(2),
                                    Duration.ofSeconds(2)),
                            (host, resolvedAddress) -> observedPinnedAddress.set(resolvedAddress));
            ProviderConnectionTester tester =
                    new ProviderConnectionTester(
                            new ModelsProbeClient(
                                    RestClient.builder(), RestClient.builder(), new ObjectMapper()),
                            pinnedHttpClientFactory);
            BaseUrlValidator.ValidatedTarget target =
                    new BaseUrlValidator.ValidatedTarget(
                            URI.create("http://rebind.invalid:" + port), validatedAddress);

            ConnectionTestResult result =
                    tester.probeConnection(
                            LlmProvider.OPENAI,
                            target,
                            "sk-test-key".getBytes(StandardCharsets.UTF_8));

            assertThat(result.result()).isEqualTo(MasterKeyTestResult.OK);
            assertThat(result.models()).containsExactly("gpt-4o-mini");
            assertThat(requestCount.get()).isGreaterThanOrEqualTo(1);
            assertThat(observedPinnedAddress).hasValue(validatedAddress);
        } finally {
            server.stop(0);
        }
    }
}
