package com.zeromail.core.cleanup.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * UNS-04c + UNS-08c — HTTP unsubscribe POST status mapping + URL provenance/scheme guard.
 *
 * <p>Future production class {@code UnsubscribeHttpClient} (Wave 3 / Plan 05):
 *
 * <ul>
 *   <li>200 / 202 / 204 → {@code UnsubscribeResult.ok(statusCode)}
 *   <li>3xx redirect → {@code UnsubscribeResult.failed("HTTP_3XX_REDIRECT")}
 *   <li>4xx → {@code UnsubscribeResult.failed("HTTP_4XX_" + code)}
 *   <li>5xx → {@code UnsubscribeResult.failed("HTTP_5XX_" + code)}
 *   <li>connect/read timeout → {@code UnsubscribeResult.failed("TIMEOUT")}
 *   <li>I/O / network error → {@code UnsubscribeResult.failed("NETWORK_ERROR")}
 *   <li>non-{@code https://} URL → throws {@code IllegalArgumentException} (parse-time + run-time
 *       defense-in-depth per CONTEXT D-11)
 * </ul>
 *
 * <p>Wave 0 RED: production class not present; WireMock fixture and the per-status assertions will
 * be wired in Wave 3 / Plan 05. This stub locks the contract surface via {@code Class.forName}
 * presence checks and a {@code https://} guard expectation.
 */
class UnsubscribeHttpClientTest {

    private static final String UNSUBSCRIBE_HTTP_CLIENT =
            "com.zeromail.core.cleanup.usecases.UnsubscribeHttpClient";
    private static final String UNSUBSCRIBE_RESULT =
            "com.zeromail.core.cleanup.domain.UnsubscribeResult";

    @Test
    void future_unsubscribe_http_client_types_are_present() {
        assertThatCode(() -> Class.forName(UNSUBSCRIBE_HTTP_CLIENT))
                .as("Future production type must exist: " + UNSUBSCRIBE_HTTP_CLIENT)
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(UNSUBSCRIBE_RESULT))
                .as("Future production type must exist: " + UNSUBSCRIBE_RESULT)
                .doesNotThrowAnyException();
    }

    @Test
    void responds200_returnsOk() throws Exception {
        Class.forName(UNSUBSCRIBE_HTTP_CLIENT);
        Object unsubscribeHttpClient = lookupBean();
        Object unsubscribeResult =
                invokePostOneClick(
                        unsubscribeHttpClient, UUID.randomUUID(), "https://provider.test/u/200");
        assertThat(unsubscribeResult).as("200 must map to OK").isNotNull();
    }

    @Test
    void responds202_returnsOk() throws Exception {
        Class.forName(UNSUBSCRIBE_HTTP_CLIENT);
        Object unsubscribeHttpClient = lookupBean();
        Object unsubscribeResult =
                invokePostOneClick(
                        unsubscribeHttpClient, UUID.randomUUID(), "https://provider.test/u/202");
        assertThat(unsubscribeResult).as("202 must map to OK").isNotNull();
    }

    @Test
    void responds204_returnsOk() throws Exception {
        Class.forName(UNSUBSCRIBE_HTTP_CLIENT);
        Object unsubscribeHttpClient = lookupBean();
        Object unsubscribeResult =
                invokePostOneClick(
                        unsubscribeHttpClient, UUID.randomUUID(), "https://provider.test/u/204");
        assertThat(unsubscribeResult).as("204 must map to OK").isNotNull();
    }

    @Test
    void responds301_returnsFailedHttp3xxRedirect() throws Exception {
        Class.forName(UNSUBSCRIBE_HTTP_CLIENT);
        Object unsubscribeHttpClient = lookupBean();
        Object unsubscribeResult =
                invokePostOneClick(
                        unsubscribeHttpClient, UUID.randomUUID(), "https://provider.test/u/301");
        assertThat(unsubscribeResult)
                .as("301 must map to FAILED with reason HTTP_3XX_REDIRECT (no redirect follow)")
                .isNotNull();
    }

    @Test
    void responds410_returnsFailedHttp4xx410() throws Exception {
        Class.forName(UNSUBSCRIBE_HTTP_CLIENT);
        Object unsubscribeHttpClient = lookupBean();
        Object unsubscribeResult =
                invokePostOneClick(
                        unsubscribeHttpClient, UUID.randomUUID(), "https://provider.test/u/410");
        assertThat(unsubscribeResult)
                .as("410 must map to FAILED with reason HTTP_4XX_410")
                .isNotNull();
    }

    @Test
    void responds500_returnsFailedHttp5xx500() throws Exception {
        Class.forName(UNSUBSCRIBE_HTTP_CLIENT);
        Object unsubscribeHttpClient = lookupBean();
        Object unsubscribeResult =
                invokePostOneClick(
                        unsubscribeHttpClient, UUID.randomUUID(), "https://provider.test/u/500");
        assertThat(unsubscribeResult)
                .as("500 must map to FAILED with reason HTTP_5XX_500")
                .isNotNull();
    }

    @Test
    void connectTimeout_returnsFailedTimeout() throws Exception {
        Class.forName(UNSUBSCRIBE_HTTP_CLIENT);
        Object unsubscribeHttpClient = lookupBean();
        Object unsubscribeResult =
                invokePostOneClick(
                        unsubscribeHttpClient, UUID.randomUUID(), "https://provider.test/u/slow");
        assertThat(unsubscribeResult)
                .as("connect timeout must map to FAILED with reason TIMEOUT")
                .isNotNull();
    }

    @Test
    void rejectsHttpUrlNotHttps() throws Exception {
        Class.forName(UNSUBSCRIBE_HTTP_CLIENT);
        Object unsubscribeHttpClient = lookupBean();

        assertThatThrownBy(
                        () ->
                                invokePostOneClick(
                                        unsubscribeHttpClient,
                                        UUID.randomUUID(),
                                        "http://provider.test/u/insecure"))
                .as("plain HTTP must be rejected at run-time (CONTEXT D-11 defense in depth)")
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private static Object lookupBean() throws Exception {
        return Class.forName(UNSUBSCRIBE_HTTP_CLIENT).getDeclaredConstructor().newInstance();
    }

    private static Object invokePostOneClick(
            Object unsubscribeHttpClient, UUID tenantId, String unsubscribeUrl) {
        try {
            return unsubscribeHttpClient
                    .getClass()
                    .getMethod("postOneClick", UUID.class, String.class)
                    .invoke(unsubscribeHttpClient, tenantId, unsubscribeUrl);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
    }
}
