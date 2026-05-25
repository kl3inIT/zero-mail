package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.support.ApiPostgresTestBase;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class CorsIntegrationTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Test
    void preflight_for_frontend_origin_allows_session_cookie_requests() {
        var response =
                corsRestClient()
                        .method(HttpMethod.OPTIONS)
                        .uri("/api/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .retrieve()
                        .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:3000");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
        assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.GET);
    }

    @Test
    void preflight_for_admin_origin_allows_session_cookie_requests() {
        var response =
                corsRestClient()
                        .method(HttpMethod.OPTIONS)
                        .uri("/api/admin/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .retrieve()
                        .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:5174");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
        assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.GET);
    }

    @Test
    void actual_response_for_frontend_origin_includes_cors_headers() {
        var response =
                corsRestClient()
                        .get()
                        .uri("/api/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .exchange(
                                (ignoredRequest, clientResponse) ->
                                        new CorsResponse(
                                                clientResponse.getStatusCode(),
                                                clientResponse.getHeaders()));

        assertThat(
                        response.statusCode().is3xxRedirection()
                                || response.statusCode().is4xxClientError())
                .isTrue();
        assertThat(response.headers().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:3000");
        assertThat(response.headers().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
    }

    @Test
    void actual_response_for_admin_origin_includes_cors_headers() {
        var response =
                corsRestClient()
                        .get()
                        .uri("/api/admin/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                        .exchange(
                                (ignoredRequest, clientResponse) ->
                                        new CorsResponse(
                                                clientResponse.getStatusCode(),
                                                clientResponse.getHeaders()));

        assertThat(
                        response.statusCode().is3xxRedirection()
                                || response.statusCode().is4xxClientError())
                .isTrue();
        assertThat(response.headers().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:5174");
        assertThat(response.headers().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
    }

    private RestClient corsRestClient() {
        HttpClient noRedirectHttpClient =
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(noRedirectHttpClient))
                .build();
    }

    private record CorsResponse(HttpStatusCode statusCode, HttpHeaders headers) {}
}
