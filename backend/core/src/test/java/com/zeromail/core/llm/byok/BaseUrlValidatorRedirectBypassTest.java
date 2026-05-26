package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmByokProperties;
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
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class BaseUrlValidatorRedirectBypassTest {

    @Test
    void provider_probe_treats_redirect_to_private_ip_as_network_error_without_following()
            throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/models",
                exchange -> {
                    requestCount.incrementAndGet();
                    exchange.getResponseHeaders()
                            .add("Location", "http://169.254.169.254/latest/meta-data/");
                    exchange.sendResponseHeaders(301, -1);
                    exchange.close();
                });
        server.start();
        try {
            int port = server.getAddress().getPort();
            ProviderConnectionTester tester = newTester();
            BaseUrlValidator.ValidatedTarget target =
                    new BaseUrlValidator.ValidatedTarget(
                            URI.create("http://localhost:" + port),
                            InetAddress.getLoopbackAddress());

            ConnectionTestResult result =
                    tester.probeConnection(
                            LlmProvider.OPENAI,
                            target,
                            "sk-test-key".getBytes(StandardCharsets.UTF_8));

            assertThat(result.result()).isEqualTo(MasterKeyTestResult.NETWORK_ERROR);
            assertThat(result.models()).isEmpty();
            assertThat(requestCount).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    private static ProviderConnectionTester newTester() {
        ModelsProbeClient modelsProbeClient =
                new ModelsProbeClient(
                        RestClient.builder(), RestClient.builder(), new ObjectMapper());
        PinnedHttpClientFactory pinnedHttpClientFactory =
                new PinnedHttpClientFactory(
                        new ZeroMailLlmByokProperties(
                                false,
                                List.of(),
                                List.of(),
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(2)),
                        PinnedHttpClientFactory.PinnedResolutionObserver.noop());
        return new ProviderConnectionTester(modelsProbeClient, pinnedHttpClientFactory);
    }
}
