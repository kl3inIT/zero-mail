package com.zeromail.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import com.zeromail.api.support.ApiPostgresTestBase;

import static org.assertj.core.api.Assertions.assertThat;

class CorsIntegrationTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Test
    void preflight_for_frontend_origin_allows_session_cookie_requests() {
        var response = RestClient.create("http://localhost:" + port)
                .method(HttpMethod.OPTIONS)
                .uri("/me")
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
    void actual_response_for_frontend_origin_includes_cors_headers() {
        var response = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .exchange((request, clientResponse) ->
                        new CorsResponse(clientResponse.getStatusCode(), clientResponse.getHeaders()));

        assertThat(response.statusCode().is3xxRedirection()).isTrue();
        assertThat(response.headers().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:3000");
        assertThat(response.headers().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
    }

    private record CorsResponse(HttpStatusCode statusCode, HttpHeaders headers) {}
}
