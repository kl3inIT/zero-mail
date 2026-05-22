package com.zeromail.core.rules.persistence;

import com.zeromail.core.rules.projection.RuleAutomationSettingsView;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RuleAutomationSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public RuleAutomationSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public RuleAutomationSettingsView getOrCreate(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        jdbcTemplate.update(
                """
                INSERT INTO rule_automation_settings(tenant_id, auto_send_rules_enabled)
                VALUES (?, TRUE)
                ON CONFLICT (tenant_id) DO NOTHING
                """,
                tenantId);
        List<Boolean> values =
                jdbcTemplate.queryForList(
                        """
                        SELECT auto_send_rules_enabled
                        FROM rule_automation_settings
                        WHERE tenant_id = ?
                        """,
                        Boolean.class,
                        tenantId);
        if (values.isEmpty()) {
            throw new IllegalStateException("rule automation settings row was not created");
        }
        return new RuleAutomationSettingsView(values.getFirst());
    }

    public RuleAutomationSettingsView update(UUID tenantId, boolean autoSendRulesEnabled) {
        Objects.requireNonNull(tenantId, "tenantId");
        jdbcTemplate.update(
                """
                INSERT INTO rule_automation_settings(
                    tenant_id, auto_send_rules_enabled, updated_at, version
                )
                VALUES (?, ?, NOW(), 0)
                ON CONFLICT (tenant_id) DO UPDATE
                SET auto_send_rules_enabled = EXCLUDED.auto_send_rules_enabled,
                    updated_at = NOW(),
                    version = rule_automation_settings.version + 1
                """,
                tenantId,
                autoSendRulesEnabled);
        return getOrCreate(tenantId);
    }
}
