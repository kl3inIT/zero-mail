package com.zeromail.core.rules.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RuleCatalogSeedMigrationTest extends PostgresContainerTest {

    private static final List<String> PERSONA_KEYS =
            List.of(
                    "founder",
                    "influencer",
                    "realtor",
                    "investor",
                    "assistant",
                    "developer",
                    "designer",
                    "sales",
                    "marketer",
                    "support",
                    "recruiter",
                    "student",
                    "outreach",
                    "other");

    private static final List<String> ACTION_KEYS =
            List.of(
                    "label",
                    "archive",
                    "save_draft",
                    "mark_read",
                    "star",
                    "add_to_digest",
                    "mark_spam",
                    "send_reply",
                    "forward_email",
                    "send_email");

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void seed_contains_required_personas_examples_actions_and_default_auto_send_setting() {
        List<String> personaKeys =
                jdbcTemplate.queryForList(
                        """
                        SELECT persona_key
                        FROM rule_example_persona
                        WHERE enabled = TRUE
                        ORDER BY display_order
                        """,
                        String.class);
        assertThat(personaKeys).containsExactlyElementsOf(PERSONA_KEYS);

        Integer blankPromptCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM rule_example_prompt
                        WHERE enabled = TRUE
                          AND (
                            prompt_en IS NULL OR btrim(prompt_en) = ''
                            OR prompt_vi IS NULL OR btrim(prompt_vi) = ''
                          )
                        """,
                        Integer.class);
        assertThat(blankPromptCount).isZero();

        Map<String, Integer> exampleCountsByPersona =
                jdbcTemplate.query(
                        """
                        SELECT persona.persona_key, COUNT(prompt.prompt_id)::int AS prompt_count
                        FROM rule_example_persona persona
                        JOIN rule_example_prompt prompt ON prompt.persona_id = persona.persona_id
                        WHERE persona.enabled = TRUE
                          AND prompt.enabled = TRUE
                        GROUP BY persona.persona_key
                        """,
                        resultSet -> {
                            java.util.LinkedHashMap<String, Integer> counts =
                                    new java.util.LinkedHashMap<>();
                            while (resultSet.next()) {
                                counts.put(
                                        resultSet.getString("persona_key"),
                                        resultSet.getInt("prompt_count"));
                            }
                            return counts;
                        });
        assertThat(exampleCountsByPersona.keySet())
                .containsExactlyInAnyOrderElementsOf(PERSONA_KEYS);
        assertThat(exampleCountsByPersona.values())
                .allSatisfy(count -> assertThat(count).isPositive());

        List<String> actionKeys =
                jdbcTemplate.queryForList(
                        """
                        SELECT action_key
                        FROM rule_action_descriptor
                        WHERE enabled = TRUE
                        ORDER BY display_order
                        """,
                        String.class);
        assertThat(actionKeys).containsExactlyElementsOf(ACTION_KEYS);

        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, "seed");
        jdbcTemplate.update("INSERT INTO rule_automation_settings(tenant_id) VALUES (?)", tenantId);
        Boolean autoSendRulesEnabled =
                jdbcTemplate.queryForObject(
                        "SELECT auto_send_rules_enabled FROM rule_automation_settings WHERE tenant_id = ?",
                        Boolean.class,
                        tenantId);
        assertThat(autoSendRulesEnabled).isTrue();
    }
}
