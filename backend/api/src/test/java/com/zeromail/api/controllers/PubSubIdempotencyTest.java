package com.zeromail.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.api.support.MockGoogleOidcServer;

@Disabled("Wave 0 RED scaffold - enable after Plan 03 adds endpoint")
class PubSubIdempotencyTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Autowired JdbcTemplate jdbc;

    @Test
    void duplicatePushMessage_sameMessageId_onlyOnePubSubDeliveryRow() {
        String messageId = UUID.randomUUID().toString();
        RestClient client = RestClient.create("http://localhost:" + port);
        MockGoogleOidcServer oidc = new MockGoogleOidcServer();

        assertThat(client).isNotNull();
        assertThat(oidc).isNotNull();
        assertThat(countRows(messageId)).as("UNIQUE(tenant_id, pubsub_message_id) dedup contract").isZero();

        throw new TestAbortedException("RED scaffold - remove @Disabled when Plan 03 is complete");
    }

    @Test
    void unknownEmailAddress_returns200_noPubSubDeliveryRow() {
        String messageId = UUID.randomUUID().toString();

        assertThat(countRows(messageId)).isZero();

        throw new TestAbortedException("RED scaffold - remove @Disabled when Plan 03 is complete");
    }

    private Long countRows(String messageId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pubsub_delivery WHERE pubsub_message_id = ?",
                Long.class,
                messageId);
    }
}
