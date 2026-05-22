package com.zeromail.core.rules.catalog;

import org.springframework.jdbc.core.JdbcTemplate;

final class RuleCatalogTestFixtures {

    private RuleCatalogTestFixtures() {}

    static void resetSeedCatalog(JdbcTemplate jdbcTemplate) {
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
        jdbcTemplate.execute(
                """
                UPDATE rule_example_prompt
                SET enabled = TRUE,
                    prompt_vi = CASE source_ref
                        WHEN 'inbox-zero:founder:001'
                            THEN 'Gắn nhãn email từ địa chỉ @mycompany.com là @[Team]'
                        ELSE prompt_vi
                    END
                WHERE source_ref LIKE 'inbox-zero:%'
                """);
        jdbcTemplate.execute(
                """
                UPDATE rule_example_persona
                SET display_order = display_order + 1000000
                WHERE persona_key IN (
                    'founder','influencer','realtor','investor','assistant','developer',
                    'designer','sales','marketer','support','recruiter','student','outreach','other'
                )
                """);
        jdbcTemplate.execute(
                """
                UPDATE rule_example_persona
                SET enabled = TRUE,
                    display_order = CASE persona_key
                        WHEN 'founder' THEN 10
                        WHEN 'influencer' THEN 20
                        WHEN 'realtor' THEN 30
                        WHEN 'investor' THEN 40
                        WHEN 'assistant' THEN 50
                        WHEN 'developer' THEN 60
                        WHEN 'designer' THEN 70
                        WHEN 'sales' THEN 80
                        WHEN 'marketer' THEN 90
                        WHEN 'support' THEN 100
                        WHEN 'recruiter' THEN 110
                        WHEN 'student' THEN 120
                        WHEN 'outreach' THEN 130
                        WHEN 'other' THEN 140
                        ELSE display_order
                    END
                WHERE persona_key IN (
                    'founder','influencer','realtor','investor','assistant','developer',
                    'designer','sales','marketer','support','recruiter','student','outreach','other'
                )
                """);
        jdbcTemplate.execute(
                """
                UPDATE rule_action_descriptor
                SET display_order = display_order + 1000000
                WHERE action_key IN (
                    'label','archive','save_draft','mark_read','star','add_to_digest',
                    'mark_spam','send_reply','forward_email','send_email'
                )
                """);
        jdbcTemplate.execute(
                """
                UPDATE rule_action_descriptor
                SET enabled = TRUE,
                    display_order = CASE action_key
                        WHEN 'label' THEN 10
                        WHEN 'archive' THEN 20
                        WHEN 'save_draft' THEN 30
                        WHEN 'mark_read' THEN 40
                        WHEN 'star' THEN 50
                        WHEN 'add_to_digest' THEN 60
                        WHEN 'mark_spam' THEN 70
                        WHEN 'send_reply' THEN 80
                        WHEN 'forward_email' THEN 90
                        WHEN 'send_email' THEN 100
                        ELSE display_order
                    END
                WHERE action_key IN (
                    'label','archive','save_draft','mark_read','star','add_to_digest',
                    'mark_spam','send_reply','forward_email','send_email'
                )
                """);
    }

    static void resetAdminAudit(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DELETE FROM admin_read_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event DISABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("DELETE FROM admin_audit_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event ENABLE TRIGGER admin_audit_event_append_only");
    }
}
