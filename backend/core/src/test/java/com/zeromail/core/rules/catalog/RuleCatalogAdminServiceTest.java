package com.zeromail.core.rules.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import com.zeromail.core.rules.catalog.usecases.RuleActionDescriptorOrderEntry;
import com.zeromail.core.rules.catalog.usecases.RuleActionDescriptorWriteCommand;
import com.zeromail.core.rules.catalog.usecases.RuleCatalogAdminService;
import com.zeromail.core.rules.catalog.usecases.RuleCatalogOrderEntry;
import com.zeromail.core.rules.catalog.usecases.RulePersonaWriteCommand;
import com.zeromail.core.rules.catalog.usecases.RulePromptWriteCommand;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RuleCatalogAdminServiceTest extends PostgresContainerTest {

    private static final UUID ADMIN_USER_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000871");
    private static final AdminUser ADMIN_USER =
            new AdminUser(
                    ADMIN_USER_ID,
                    "rule-catalog-admin@example.com",
                    AdminStatus.ACTIVE,
                    Optional.of("Rule Catalog Admin"));

    @Autowired private RuleCatalogAdminService ruleCatalogAdminService;

    @Autowired private AdminUserRepository adminUserRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetAdminState() {
        jdbcTemplate.execute("DELETE FROM admin_read_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event DISABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("DELETE FROM admin_audit_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event ENABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("DELETE FROM rule_action_descriptor WHERE action_key = 'qa_action'");
        jdbcTemplate.execute(
                """
                DELETE FROM rule_example_prompt
                WHERE persona_id IN (
                    SELECT persona_id
                    FROM rule_example_persona
                    WHERE persona_key = 'qa_persona'
                )
                """);
        jdbcTemplate.execute("DELETE FROM rule_example_persona WHERE persona_key = 'qa_persona'");
        jdbcTemplate.execute("DELETE FROM admin_users");
        adminUserRepository.save(
                new AdminUserEntity(
                        ADMIN_USER_ID,
                        ADMIN_USER.email(),
                        "Rule Catalog Admin",
                        new byte[] {0x72},
                        AdminStatus.ACTIVE));
    }

    @Test
    void admin_mutations_persist_changes_and_append_audit_rows() {
        UUID requestId = UUID.fromString("00000000-0000-4000-8000-000000000872");

        AdminContext.run(
                ADMIN_USER,
                () -> {
                    UUID personaId =
                            ruleCatalogAdminService.createPersona(
                                    new RulePersonaWriteCommand(
                                            "qa_persona",
                                            "QA Persona",
                                            "Persona kiểm thử",
                                            "sparkles",
                                            990,
                                            true),
                                    "127.0.0.1",
                                    requestId);
                    ruleCatalogAdminService.updatePersona(
                            personaId,
                            new RulePersonaWriteCommand(
                                    "qa_persona",
                                    "QA Persona Updated",
                                    "Persona kiểm thử cập nhật",
                                    "sparkles",
                                    991,
                                    true),
                            "localize persona",
                            "127.0.0.1",
                            requestId);
                    UUID promptId =
                            ruleCatalogAdminService.createPrompt(
                                    personaId,
                                    new RulePromptWriteCommand(
                                            "Label QA emails as @[QA]",
                                            "Gắn nhãn email QA là @[QA]",
                                            10,
                                            true,
                                            "test:qa:001"),
                                    "create prompt",
                                    "127.0.0.1",
                                    requestId);
                    ruleCatalogAdminService.updatePrompt(
                            promptId,
                            new RulePromptWriteCommand(
                                    "Label QA emails as @[QA Updated]",
                                    "Gắn nhãn email QA là @[QA Updated]",
                                    20,
                                    true,
                                    "test:qa:001"),
                            "update prompt",
                            "127.0.0.1",
                            requestId);
                    ruleCatalogAdminService.setPromptEnabled(
                            promptId, false, "disable prompt", "127.0.0.1", requestId);
                    ruleCatalogAdminService.setPersonaEnabled(
                            personaId, false, "disable persona", "127.0.0.1", requestId);
                    ruleCatalogAdminService.upsertActionDescriptor(
                            new RuleActionDescriptorWriteCommand(
                                    "qa_action",
                                    "QA Action",
                                    "Hành động QA",
                                    "Used by tests.",
                                    "Dùng cho test.",
                                    "LOW",
                                    "AVAILABLE",
                                    990,
                                    true),
                            "create action descriptor",
                            "127.0.0.1",
                            requestId);
                    ruleCatalogAdminService.setActionDescriptorEnabled(
                            "qa_action", false, "disable action", "127.0.0.1", requestId);
                });

        Boolean personaEnabled =
                jdbcTemplate.queryForObject(
                        "SELECT enabled FROM rule_example_persona WHERE persona_key = 'qa_persona'",
                        Boolean.class);
        Boolean promptEnabled =
                jdbcTemplate.queryForObject(
                        "SELECT enabled FROM rule_example_prompt WHERE source_ref = 'test:qa:001'",
                        Boolean.class);
        Boolean actionEnabled =
                jdbcTemplate.queryForObject(
                        "SELECT enabled FROM rule_action_descriptor WHERE action_key = 'qa_action'",
                        Boolean.class);

        assertThat(personaEnabled).isFalse();
        assertThat(promptEnabled).isFalse();
        assertThat(actionEnabled).isFalse();

        List<String> auditActions =
                jdbcTemplate.queryForList(
                        "SELECT action FROM admin_audit_event ORDER BY chain_index", String.class);
        assertThat(auditActions)
                .containsExactly(
                        "RULE_CATALOG_PERSONA_CREATED",
                        "RULE_CATALOG_PERSONA_UPDATED",
                        "RULE_CATALOG_PROMPT_CREATED",
                        "RULE_CATALOG_PROMPT_UPDATED",
                        "RULE_CATALOG_PROMPT_DISABLED",
                        "RULE_CATALOG_PERSONA_DISABLED",
                        "RULE_CATALOG_ACTION_DESCRIPTOR_UPSERTED",
                        "RULE_CATALOG_ACTION_DESCRIPTOR_DISABLED");
    }

    @Test
    void reorder_operations_persist_deterministic_display_order_and_are_audited() {
        UUID founderPersonaId =
                jdbcTemplate.queryForObject(
                        "SELECT persona_id FROM rule_example_persona WHERE persona_key = 'founder'",
                        UUID.class);
        UUID influencerPersonaId =
                jdbcTemplate.queryForObject(
                        "SELECT persona_id FROM rule_example_persona WHERE persona_key = 'influencer'",
                        UUID.class);

        AdminContext.run(
                ADMIN_USER,
                () -> {
                    ruleCatalogAdminService.reorderPersonas(
                            List.of(
                                    new RuleCatalogOrderEntry(influencerPersonaId, 10),
                                    new RuleCatalogOrderEntry(founderPersonaId, 20)),
                            "reorder personas",
                            "127.0.0.1",
                            UUID.fromString("00000000-0000-4000-8000-000000000873"));
                    ruleCatalogAdminService.reorderActionDescriptors(
                            List.of(
                                    new RuleActionDescriptorOrderEntry("archive", 10),
                                    new RuleActionDescriptorOrderEntry("label", 20)),
                            "reorder actions",
                            "127.0.0.1",
                            UUID.fromString("00000000-0000-4000-8000-000000000874"));
                });

        List<String> firstTwoPersonas =
                jdbcTemplate.queryForList(
                        """
                        SELECT persona_key
                        FROM rule_example_persona
                        ORDER BY display_order
                        LIMIT 2
                        """,
                        String.class);
        List<String> firstTwoActions =
                jdbcTemplate.queryForList(
                        """
                        SELECT action_key
                        FROM rule_action_descriptor
                        ORDER BY display_order
                        LIMIT 2
                        """,
                        String.class);

        assertThat(firstTwoPersonas).containsExactly("influencer", "founder");
        assertThat(firstTwoActions).containsExactly("archive", "label");
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT action FROM admin_audit_event ORDER BY chain_index",
                                String.class))
                .containsExactly(
                        "RULE_CATALOG_PERSONAS_REORDERED",
                        "RULE_CATALOG_ACTION_DESCRIPTORS_REORDERED");
    }
}
