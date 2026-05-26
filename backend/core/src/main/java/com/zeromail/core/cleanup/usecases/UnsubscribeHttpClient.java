package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeResult;
import com.zeromail.core.shared.net.OutboundHostGuard;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The only class in {@code ..core.cleanup..} allowed to construct or obtain a {@link HttpClient} or
 * Spring {@link RestClient} — enforced architecturally by Wave 0 {@code
 * UnsubscribeHttpClientBoundaryTest}.
 *
 * <p>RFC 8058 §3 one-click unsubscribe POST gateway. Per D-07:
 *
 * <ul>
 *   <li>Redirect policy = {@code NEVER} (RFC 8058 §3 forbids redirect-following — a 3xx must be
 *       reported as failure, not auto-followed).
 *   <li>Connect timeout = 5s. Read timeout = 10s.
 *   <li>Body literal {@code List-Unsubscribe=One-Click}, Content-Type {@code
 *       application/x-www-form-urlencoded}.
 *   <li>No cookies, no Authorization header — Spring {@link RestClient} default behavior.
 * </ul>
 *
 * <p>Per D-08 success gate: HTTP 200/201/202/204 → {@link UnsubscribeResult.Ok}; 3xx → {@code
 * Failed("HTTP_3XX_REDIRECT")}; 4xx → {@code Failed("HTTP_4XX_<code>")}; 5xx → {@code
 * Failed("HTTP_5XX_<code>")}; connect timeout → {@code Failed("TIMEOUT")}; other IO/network →
 * {@code Failed("NETWORK_ERROR")}.
 *
 * <p>Per D-11 execute-time defense-in-depth: rejects any non-HTTPS URL with {@link
 * IllegalArgumentException} even though parse-time persistence already drops {@code http://} URLs.
 *
 * <p>Privacy invariant (UNS-09): log lines contain only {@code tenantId}, {@code senderDomain}
 * ({@code URI.getHost()}), and {@code statusCode}. Never logs the full URL, request body, or
 * response body.
 *
 * <p>Provenance check (UNS-08c: "URL must come from persisted {@code list_unsubscribe_url}") is
 * delegated to the Wave 4b campaign orchestrator — Single Responsibility: this client only performs
 * the HTTP POST.
 */
@Component
public class UnsubscribeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeHttpClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final String HTTPS_SCHEME = "https";
    private static final String FORM_FIELD_NAME = "List-Unsubscribe";
    private static final String FORM_FIELD_VALUE = "One-Click";

    private final RestClient unsubscribeRestClient;
    private final OutboundHostGuard outboundHostGuard;

    public UnsubscribeHttpClient(OutboundHostGuard outboundHostGuard) {
        this.outboundHostGuard = outboundHostGuard;
        HttpClient nativeHttpClient =
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(nativeHttpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.unsubscribeRestClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * Issue the RFC 8058 §3 one-click POST against {@code unsubscribeUrl}.
     *
     * @param tenantId tenant on whose behalf the unsubscribe is performed (logged only — no
     *     authorization header is sent per RFC 8058).
     * @param unsubscribeUrl the {@code list_unsubscribe_url} value persisted at ingest. MUST start
     *     with {@code https://}.
     * @return {@link UnsubscribeResult.Ok} on HTTP 200/201/202/204, otherwise {@link
     *     UnsubscribeResult.Failed} with a stable {@code failureReason} token.
     * @throws IllegalArgumentException if {@code unsubscribeUrl} is null/blank or not an {@code
     *     https://} URL (D-11 defense-in-depth).
     */
    public UnsubscribeResult postOneClick(UUID tenantId, String unsubscribeUrl) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (unsubscribeUrl == null || unsubscribeUrl.isBlank()) {
            throw new IllegalArgumentException("unsubscribeUrl must not be null or blank");
        }
        URI validatedHttpsUrl = parseAndValidateHttps(unsubscribeUrl);
        String senderDomain =
                validatedHttpsUrl.getHost() == null ? "unknown" : validatedHttpsUrl.getHost();

        if (outboundHostGuard.isBlocked(senderDomain)) {
            log.warn(
                    "event=cleanup_unsubscribe_http_post_failed tenantId={} senderDomain={} failureReason=BLOCKED_PRIVATE_HOST",
                    tenantId,
                    senderDomain);
            return UnsubscribeResult.failed("BLOCKED_PRIVATE_HOST");
        }

        LinkedMultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add(FORM_FIELD_NAME, FORM_FIELD_VALUE);

        try {
            return unsubscribeRestClient
                    .post()
                    .uri(validatedHttpsUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formBody)
                    .exchange(
                            (httpRequest, httpResponse) -> {
                                int statusCode = httpResponse.getStatusCode().value();
                                return mapStatusCode(tenantId, senderDomain, statusCode);
                            },
                            false);
        } catch (ResourceAccessException accessFailure) {
            String reasonCode =
                    accessFailure.getCause() instanceof HttpConnectTimeoutException
                            ? "TIMEOUT"
                            : "NETWORK_ERROR";
            log.warn(
                    "event=cleanup_unsubscribe_http_post_failed tenantId={} senderDomain={} failureReason={}",
                    tenantId,
                    senderDomain,
                    reasonCode);
            return UnsubscribeResult.failed(reasonCode);
        } catch (RestClientException restClientFailure) {
            log.warn(
                    "event=cleanup_unsubscribe_http_post_failed tenantId={} senderDomain={} failureReason=NETWORK_ERROR",
                    tenantId,
                    senderDomain);
            return UnsubscribeResult.failed("NETWORK_ERROR");
        }
    }

    private static URI parseAndValidateHttps(String unsubscribeUrl) {
        URI parsedUri;
        try {
            parsedUri = new URI(unsubscribeUrl);
        } catch (URISyntaxException malformedUri) {
            throw new IllegalArgumentException("Malformed unsubscribe HTTPS URI");
        }
        String scheme = parsedUri.getScheme();
        if (scheme == null || !HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only https URLs accepted: scheme=" + scheme);
        }
        return parsedUri;
    }

    private static UnsubscribeResult mapStatusCode(
            UUID tenantId, String senderDomain, int statusCode) {
        if (statusCode == 200 || statusCode == 201 || statusCode == 202 || statusCode == 204) {
            log.info(
                    "event=cleanup_unsubscribe_http_post tenantId={} senderDomain={} statusCode={}",
                    tenantId,
                    senderDomain,
                    statusCode);
            return UnsubscribeResult.ok(String.valueOf(statusCode));
        }
        if (statusCode >= 300 && statusCode < 400) {
            log.warn(
                    "event=cleanup_unsubscribe_http_post_failed tenantId={} senderDomain={} failureReason=HTTP_3XX_REDIRECT statusCode={}",
                    tenantId,
                    senderDomain,
                    statusCode);
            return UnsubscribeResult.failed("HTTP_3XX_REDIRECT");
        }
        String reasonCode =
                (statusCode >= 400 && statusCode < 500)
                        ? "HTTP_4XX_" + statusCode
                        : "HTTP_5XX_" + statusCode;
        log.warn(
                "event=cleanup_unsubscribe_http_post_failed tenantId={} senderDomain={} failureReason={}",
                tenantId,
                senderDomain,
                reasonCode);
        return UnsubscribeResult.failed(reasonCode);
    }
}
