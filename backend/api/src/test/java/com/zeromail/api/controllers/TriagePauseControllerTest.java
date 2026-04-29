package com.zeromail.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.zeromail.api.support.ApiPostgresTestBase;

@Disabled("Wave 0 RED scaffold - enable after Plan 03 adds endpoint")
class TriagePauseControllerTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Test
    void putTriagePause_true_persists_triage_paused() {
        RestClient client = RestClient.create("http://localhost:" + port);

        String raw = client.put().uri("/tenant/triage-pause")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"paused\":true}")
                .retrieve()
                .body(String.class);

        assertThat(raw).contains("\"paused\":true");
    }

    @Test
    void putTriagePause_false_clears_triage_paused() {
        RestClient client = RestClient.create("http://localhost:" + port);

        String raw = client.put().uri("/tenant/triage-pause")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"paused\":false}")
                .retrieve()
                .body(String.class);

        assertThat(raw).contains("\"paused\":false");
    }
}
