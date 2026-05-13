package com.zeromail.core.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationPreferenceBackfillTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void backfillSql_insertsOneEmailPreferencePerExistingTenantIdempotently() {
        List<UUID> tenantIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (UUID tenantId : tenantIds) {
            jdbcTemplate.update(
                    "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                    tenantId,
                    "tenant-" + tenantId);
        }

        runBackfillSql();
        runBackfillSql();

        for (UUID tenantId : tenantIds) {
            NotificationPreferenceSnapshot snapshot =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT channel, digest_enabled, digest_send_hour_local
                            FROM notification_preference
                            WHERE tenant_id = ?
                            """,
                            (resultSet, rowNumber) ->
                                    new NotificationPreferenceSnapshot(
                                            resultSet.getString("channel"),
                                            resultSet.getBoolean("digest_enabled"),
                                            resultSet.getInt("digest_send_hour_local")),
                            tenantId);
            assertThat(snapshot).isEqualTo(new NotificationPreferenceSnapshot("EMAIL", true, 20));
        }

        Long backfilledPreferenceCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM notification_preference
                        WHERE tenant_id IN (?, ?, ?)
                        """,
                        Long.class,
                        tenantIds.get(0),
                        tenantIds.get(1),
                        tenantIds.get(2));
        assertThat(backfilledPreferenceCount).isEqualTo(tenantIds.size());
    }

    @Test
    void schema_usesSingleUuidPrimaryKeyPlusTenantChannelUniqueKey() {
        assertThat(constraintDefinition("notification_preference_pkey"))
                .contains("PRIMARY KEY (id)");
        assertThat(constraintDefinition("uq_notification_preference_tenant_channel"))
                .contains("UNIQUE (tenant_id, channel)");
        assertThat(indexDefinition("idx_notification_preference_due"))
                .contains("WHERE")
                .contains("digest_enabled = true")
                .contains("'EMAIL'::text");
    }

    private void runBackfillSql() {
        jdbcTemplate.update(
                """
                INSERT INTO notification_preference
                  (id, tenant_id, channel, digest_enabled, digest_send_hour_local, created_at, updated_at, version)
                SELECT gen_random_uuid(), tenants.id, 'EMAIL', true, 20, now(), now(), 0
                FROM tenants
                ON CONFLICT (tenant_id, channel) DO NOTHING
                """);
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, indexName);
    }

    private String constraintDefinition(String constraintName) {
        return jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
                String.class,
                constraintName);
    }

    private record NotificationPreferenceSnapshot(
            String channel, boolean digestEnabled, int digestSendHourLocal) {}
}
