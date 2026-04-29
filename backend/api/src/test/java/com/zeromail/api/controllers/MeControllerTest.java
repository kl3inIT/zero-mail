package com.zeromail.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import com.zeromail.api.support.ApiPostgresTestBase;

@Disabled("Wave 0 RED scaffold - enable after Plan 03 extends MeResponse")
class MeControllerTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Test
    void me_response_contains_triagePaused_field() {
        RestClient client = RestClient.create("http://localhost:" + port);
        String raw = client.get().uri("/me").retrieve().body(String.class);

        assertThat(raw).contains("\"triagePaused\"");
    }

    @Test
    void me_response_contains_gmailConnectionStatus_with_ingestionHealth() {
        RestClient client = RestClient.create("http://localhost:" + port);
        String raw = client.get().uri("/me").retrieve().body(String.class);

        assertThat(raw).contains("\"gmailConnectionStatus\"");
        assertThat(raw).contains("\"ingestionHealth\"");
    }

    @Test
    void me_response_json_shape_serializes_cleanly() {
        RestClient client = RestClient.create("http://localhost:" + port);
        String raw = client.get().uri("/me").retrieve().body(String.class);

        assertThat(raw).contains("\"triagePaused\"");
        assertThat(raw).contains("\"ingestionHealth\"");
    }
}
