package com.zeromail.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.zeromail.core.config.ZeroMailCoreProperties.BillingProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.BillingProperties.BillingCostProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.BillingProperties.BillingPaymentAccountProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.BillingProperties.BillingSepayProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.CryptoProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.LlmProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmByokProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
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
                            .restClientBuilder(properties())
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

    private static ZeroMailCoreProperties properties() {
        return new ZeroMailCoreProperties(
                new CryptoProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
                null,
                new BillingProperties(
                        new BillingSepayProperties("test-sepay-key-fixture"),
                        new BillingPaymentAccountProperties(
                                "VCB", "Vietcombank", "0000000000", "ZERO MAIL", ""),
                        new BillingCostProperties(0),
                        1000,
                        5,
                        Duration.ofHours(24)),
                new LlmProperties(
                        new ZeroMailLlmProperties(
                                null,
                                null,
                                "test-platform-key",
                                null,
                                null,
                                null,
                                Duration.ofSeconds(5),
                                Duration.ofSeconds(30)),
                        new ZeroMailLlmByokProperties(
                                false, List.of(), Duration.ofSeconds(5), Duration.ofSeconds(15))));
    }
}
