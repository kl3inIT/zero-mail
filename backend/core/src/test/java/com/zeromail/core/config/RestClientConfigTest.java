package com.zeromail.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.zeromail.core.llm.config.LlmProperties;
import com.zeromail.core.llm.config.LlmProperties.ByokProperties;
import com.zeromail.core.llm.config.LlmProperties.PlatformProperties;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

class RestClientConfigTest {

    @Test
    void rest_client_builder_disables_redirects_for_byok_validation_probes() throws Exception {
        AtomicInteger redirectedEndpointHits = new AtomicInteger();
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/redirect",
                exchange -> {
                    exchange.getResponseHeaders()
                            .add(
                                    "Location",
                                    "http://127.0.0.1:" + server.getAddress().getPort() + "/final");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        server.createContext(
                "/final",
                exchange -> {
                    redirectedEndpointHits.incrementAndGet();
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });
        server.start();
        try {
            HttpStatusCode statusCode =
                    new RestClientConfig()
                            .restClientBuilder(llmProperties())
                            .build()
                            .get()
                            .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect")
                            .exchange(
                                    (request, response) -> {
                                        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
                                        return response.getStatusCode();
                                    });

            assertThat(statusCode.value()).isEqualTo(302);
            assertThat(redirectedEndpointHits).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    private static LlmProperties llmProperties() {
        return new LlmProperties(
                new PlatformProperties(
                        null,
                        null,
                        "test-platform-key",
                        null,
                        null,
                        null,
                        null,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30)),
                new ByokProperties(
                        false,
                        List.of(),
                        List.of(),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(15)));
    }
}
