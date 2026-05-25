package com.zeromail.core.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.rules.usecases.RuleAutomationSettingsService;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RuleAutomationSettingsServiceTest extends PostgresContainerTest {

    @Autowired private RuleAutomationSettingsService ruleAutomationSettingsService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void settings_default_on_and_persist_off_per_tenant() {
        UUID firstTenantId = seedTenant("automation-settings-a");
        UUID secondTenantId = seedTenant("automation-settings-b");

        assertThat(
                        ruleAutomationSettingsService
                                .readOrDefault(firstTenantId)
                                .autoSendRulesEnabled())
                .isTrue();
        assertThat(settingsRowCount(firstTenantId)).isZero();
        assertThat(
                        ruleAutomationSettingsService
                                .update(firstTenantId, false)
                                .autoSendRulesEnabled())
                .isFalse();

        assertThat(
                        ruleAutomationSettingsService
                                .readOrDefault(firstTenantId)
                                .autoSendRulesEnabled())
                .isFalse();
        assertThat(
                        ruleAutomationSettingsService
                                .readOrDefault(secondTenantId)
                                .autoSendRulesEnabled())
                .isTrue();
        assertThat(settingsRowCount(secondTenantId)).isZero();
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, displayName);
        return tenantId;
    }

    private int settingsRowCount(UUID tenantId) {
        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rule_automation_settings WHERE tenant_id = ?",
                        Integer.class,
                        tenantId);
        return rowCount == null ? 0 : rowCount;
    }
}
