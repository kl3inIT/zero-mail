package com.zeromail.core.rules.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.rules.catalog.domain.RuleCatalogLocale;
import com.zeromail.core.rules.catalog.projection.RuleActionDescriptorView;
import com.zeromail.core.rules.catalog.projection.RuleExamplePersonaView;
import com.zeromail.core.rules.catalog.usecases.RuleCatalogUserService;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RuleCatalogUserServiceTest extends PostgresContainerTest {

    @Autowired private RuleCatalogUserService ruleCatalogUserService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void example_catalog_filters_disabled_rows_and_uses_locale_fallback() {
        UUID founderPersonaId =
                jdbcTemplate.queryForObject(
                        "SELECT persona_id FROM rule_example_persona WHERE persona_key = 'founder'",
                        UUID.class);
        UUID firstFounderPromptId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT prompt_id
                        FROM rule_example_prompt
                        WHERE persona_id = ?
                        ORDER BY display_order
                        LIMIT 1
                        """,
                        UUID.class,
                        founderPersonaId);
        jdbcTemplate.update(
                "UPDATE rule_example_prompt SET prompt_vi = '' WHERE prompt_id = ?",
                firstFounderPromptId);
        jdbcTemplate.update(
                """
                UPDATE rule_example_prompt
                SET enabled = FALSE
                WHERE source_ref = 'inbox-zero:founder:002'
                """);
        jdbcTemplate.update(
                "UPDATE rule_example_persona SET enabled = FALSE WHERE persona_key = 'other'");

        List<RuleExamplePersonaView> personas =
                ruleCatalogUserService.listExamplePersonas(RuleCatalogLocale.VIETNAMESE);

        assertThat(personas).extracting(RuleExamplePersonaView::personaKey).doesNotContain("other");
        assertThat(personas)
                .isSortedAccordingTo(Comparator.comparingInt(RuleExamplePersonaView::displayOrder));

        RuleExamplePersonaView founder =
                personas.stream()
                        .filter(persona -> persona.personaKey().equals("founder"))
                        .findFirst()
                        .orElseThrow();
        assertThat(founder.displayName()).isEqualTo("Nhà sáng lập");
        assertThat(founder.prompts())
                .extracting(prompt -> prompt.sourceRef())
                .doesNotContain("inbox-zero:founder:002");
        assertThat(founder.prompts())
                .extracting(prompt -> prompt.prompt())
                .contains("Label emails from @mycompany.com addresses as @[Team]");
    }

    @Test
    void action_catalog_filters_disabled_rows_and_localizes_labels() {
        jdbcTemplate.update(
                "UPDATE rule_action_descriptor SET enabled = FALSE WHERE action_key = 'mark_spam'");

        List<RuleActionDescriptorView> actions =
                ruleCatalogUserService.listActionDescriptors(RuleCatalogLocale.VIETNAMESE);

        assertThat(actions)
                .extracting(RuleActionDescriptorView::actionKey)
                .containsExactly(
                        "label",
                        "archive",
                        "save_draft",
                        "mark_read",
                        "star",
                        "add_to_digest",
                        "send_reply",
                        "forward_email",
                        "send_email");
        assertThat(actions)
                .filteredOn(action -> action.actionKey().equals("send_reply"))
                .singleElement()
                .satisfies(
                        action -> {
                            assertThat(action.label()).isEqualTo("Gửi trả lời");
                            assertThat(action.riskLevel()).isEqualTo("HIGH");
                            assertThat(action.availabilityStatus()).isEqualTo("AVAILABLE");
                        });
    }
}
