package com.zeromail.core.rules.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 12 W5 (CAL-TRIAGE-03, D-09): the new changeset 136 must migrate the seeded
 * system-calendar(-vi) rule template (and uncustomized materialized rules) from SEMANTIC_INTENT to
 * PRESET_CALENDAR — without touching customized rules.
 *
 * <p>This test runs against the FULL changelog (113 → 114 → 134 → 135 → 136 applied by the
 * container at boot), so by the time the test method runs, 136 has already executed. We assert the
 * post-migration state on the catalog rows directly, then insert two simulated tenants and
 * re-trigger the SQL by hand (against the post-migration shape) to verify the WHERE-clause
 * idempotency and the customized-row preservation invariant.
 */
class SystemCalendarTemplateMigrationTest extends PostgresContainerTest {

    private static final String EXPECTED_EN_AST_TYPE = "PRESET_CALENDAR";
    private static final String EN_NODE_ID = "system-calendar";
    private static final String VI_NODE_ID = "system-calendar-vi";

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void changeset_136_migrates_template_catalog_to_preset_calendar() {
        String enTemplateType =
                jdbcTemplate.queryForObject(
                        """
                        SELECT matcher_ast ->> 'type'
                        FROM rule_template_catalog
                        WHERE template_key = 'system-calendar' AND template_version = 1
                        """,
                        String.class);
        String enTemplateNodeId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT matcher_ast ->> 'nodeId'
                        FROM rule_template_catalog
                        WHERE template_key = 'system-calendar' AND template_version = 1
                        """,
                        String.class);
        String enTemplateSchemaVersion =
                jdbcTemplate.queryForObject(
                        """
                        SELECT matcher_ast ->> 'schemaVersion'
                        FROM rule_template_catalog
                        WHERE template_key = 'system-calendar' AND template_version = 1
                        """,
                        String.class);

        assertThat(enTemplateType).isEqualTo(EXPECTED_EN_AST_TYPE);
        assertThat(enTemplateNodeId).isEqualTo(EN_NODE_ID);
        assertThat(enTemplateSchemaVersion).isEqualTo("rules.v1");

        String viTemplateType =
                jdbcTemplate.queryForObject(
                        """
                        SELECT matcher_ast ->> 'type'
                        FROM rule_template_catalog
                        WHERE template_key = 'system-calendar-vi' AND template_version = 1
                        """,
                        String.class);
        String viTemplateNodeId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT matcher_ast ->> 'nodeId'
                        FROM rule_template_catalog
                        WHERE template_key = 'system-calendar-vi' AND template_version = 1
                        """,
                        String.class);

        assertThat(viTemplateType).isEqualTo(EXPECTED_EN_AST_TYPE);
        assertThat(viTemplateNodeId).isEqualTo(VI_NODE_ID);

        Integer leftoverIntentFieldCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)::int
                        FROM rule_template_catalog
                        WHERE template_key IN ('system-calendar','system-calendar-vi')
                          AND template_version = 1
                          AND jsonb_exists(matcher_ast, 'intent')
                        """,
                        Integer.class);
        assertThat(leftoverIntentFieldCount)
                .as("PRESET shape must drop the SEMANTIC_INTENT 'intent' field")
                .isZero();
    }

    @Test
    void changeset_136_migrates_uncustomized_rule_rows_and_preserves_customized_rules() {
        UUID uncustomizedTenantId = UUID.randomUUID();
        UUID customizedTenantId = UUID.randomUUID();
        UUID uncustomizedConnectionId = UUID.randomUUID();
        UUID customizedConnectionId = UUID.randomUUID();
        UUID uncustomizedRuleId = UUID.randomUUID();
        UUID customizedRuleId = UUID.randomUUID();

        insertTenant(uncustomizedTenantId, "uncustomized-tenant");
        insertTenant(customizedTenantId, "customized-tenant");
        insertGmailConnection(uncustomizedConnectionId, uncustomizedTenantId, "uncustomized");
        insertGmailConnection(customizedConnectionId, customizedTenantId, "customized");

        // Uncustomized rule: still carries the seeded SEMANTIC_INTENT shape (type=SEMANTIC_INTENT
        // AND nodeId='system-calendar'). This row must be migrated.
        insertRule(
                uncustomizedRuleId,
                uncustomizedTenantId,
                uncustomizedConnectionId,
                "system-calendar",
                """
                {"schemaVersion":"rules.v1","type":"SEMANTIC_INTENT","nodeId":"system-calendar","intent":"Calendar: meeting invitations.","deferred":true}
                """);

        // Customized rule: same template_key+version, but matcher_ast.nodeId differs from the seed
        // (user edited the matcher). This row must be PRESERVED.
        insertRule(
                customizedRuleId,
                customizedTenantId,
                customizedConnectionId,
                "system-calendar",
                """
                {"schemaVersion":"rules.v1","type":"SEMANTIC_INTENT","nodeId":"custom-cal","intent":"My custom intent","deferred":true}
                """);

        // Re-run the migration SQL by hand — exercises the same WHERE clauses as changeset 136
        // against rows inserted AFTER the changeset already executed.
        jdbcTemplate.update(
                """
                UPDATE rules
                SET matcher_ast = jsonb_build_object(
                      'schemaVersion', 'rules.v1',
                      'type', 'PRESET_CALENDAR',
                      'nodeId', 'system-calendar')
                WHERE template_key = 'system-calendar'
                  AND template_version = 1
                  AND matcher_ast ->> 'type' = 'SEMANTIC_INTENT'
                  AND matcher_ast ->> 'nodeId' = 'system-calendar'
                """);

        String uncustomizedType =
                jdbcTemplate.queryForObject(
                        "SELECT matcher_ast ->> 'type' FROM rules WHERE id = ?",
                        String.class,
                        uncustomizedRuleId);
        String uncustomizedNodeId =
                jdbcTemplate.queryForObject(
                        "SELECT matcher_ast ->> 'nodeId' FROM rules WHERE id = ?",
                        String.class,
                        uncustomizedRuleId);
        Boolean uncustomizedHasIntentField =
                jdbcTemplate.queryForObject(
                        "SELECT (jsonb_exists(matcher_ast, 'intent')) FROM rules WHERE id = ?",
                        Boolean.class,
                        uncustomizedRuleId);

        assertThat(uncustomizedType)
                .as("uncustomized seeded row migrates to PRESET_CALENDAR")
                .isEqualTo(EXPECTED_EN_AST_TYPE);
        assertThat(uncustomizedNodeId).isEqualTo(EN_NODE_ID);
        assertThat(uncustomizedHasIntentField)
                .as("PRESET shape carries no 'intent' field")
                .isFalse();

        String customizedType =
                jdbcTemplate.queryForObject(
                        "SELECT matcher_ast ->> 'type' FROM rules WHERE id = ?",
                        String.class,
                        customizedRuleId);
        String customizedNodeId =
                jdbcTemplate.queryForObject(
                        "SELECT matcher_ast ->> 'nodeId' FROM rules WHERE id = ?",
                        String.class,
                        customizedRuleId);
        String customizedIntent =
                jdbcTemplate.queryForObject(
                        "SELECT matcher_ast ->> 'intent' FROM rules WHERE id = ?",
                        String.class,
                        customizedRuleId);

        assertThat(customizedType)
                .as("customized rule with different nodeId must be PRESERVED")
                .isEqualTo("SEMANTIC_INTENT");
        assertThat(customizedNodeId).isEqualTo("custom-cal");
        assertThat(customizedIntent).isEqualTo("My custom intent");
    }

    @Test
    void changeset_136_sql_is_idempotent_via_where_clause_no_op_on_already_migrated_row() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        insertTenant(tenantId, "idempotent-tenant");
        insertGmailConnection(connectionId, tenantId, "idempotent");

        // Insert a row that is ALREADY in the PRESET shape (simulates a row migrated by an earlier
        // 136 apply against a freshly-seeded DB).
        insertRule(
                ruleId,
                tenantId,
                connectionId,
                "system-calendar",
                """
                {"schemaVersion":"rules.v1","type":"PRESET_CALENDAR","nodeId":"system-calendar"}
                """);

        int rowsAffected =
                jdbcTemplate.update(
                        """
                        UPDATE rules
                        SET matcher_ast = jsonb_build_object(
                              'schemaVersion', 'rules.v1',
                              'type', 'PRESET_CALENDAR',
                              'nodeId', 'system-calendar')
                        WHERE template_key = 'system-calendar'
                          AND template_version = 1
                          AND matcher_ast ->> 'type' = 'SEMANTIC_INTENT'
                          AND matcher_ast ->> 'nodeId' = 'system-calendar'
                          AND id = ?
                        """,
                        ruleId);

        assertThat(rowsAffected)
                .as("re-applied changeset must be a no-op against an already-migrated row")
                .isZero();
    }

    private void insertTenant(UUID tenantId, String displayName) {
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, displayName);
    }

    private void insertGmailConnection(UUID connectionId, UUID tenantId, String emailSlug) {
        jdbcTemplate.update(
                """
                INSERT INTO gmail_connections(
                    id, tenant_id, google_email, status, refresh_token_encrypted,
                    scopes_granted, connected_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, 'CONNECTED', NULL,
                    'gmail.modify', NOW(), NOW(), NOW()
                )
                """,
                connectionId,
                tenantId,
                emailSlug + "+" + connectionId + "@example.test");
    }

    private void insertRule(
            UUID ruleId,
            UUID tenantId,
            UUID gmailConnectionId,
            String templateKey,
            String matcherAstJson) {
        // matcher_ast / action_intents are jsonb columns; jdbcTemplate auto-binds strings as text,
        // so we cast at the SQL level. action_intents stays a stable label-only stub for both
        // uncustomized and customized fixtures so the test does not couple to the seed's exact
        // action JSON shape.
        jdbcTemplate.update(
                """
                INSERT INTO rules(
                    id, tenant_id, gmail_connection_id, display_name, source_text,
                    source_language, schema_version,
                    matcher_ast, action_intents,
                    enabled, order_index, template_key, template_version, customized
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    ?, ?,
                    ?::jsonb, ?::jsonb,
                    ?, ?, ?, ?, ?
                )
                """,
                ruleId,
                tenantId,
                gmailConnectionId,
                "Calendar (test fixture)",
                "Label scheduling and calendar emails as Calendar.",
                "en",
                "rules.v1",
                matcherAstJson,
                "[{\"type\":\"label\",\"labelName\":\"Calendar\"}]",
                true,
                0,
                templateKey,
                1,
                false);
    }
}
