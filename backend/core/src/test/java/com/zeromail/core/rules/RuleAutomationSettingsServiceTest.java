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

        assertThat(ruleAutomationSettingsService.getOrCreate(firstTenantId).autoSendRulesEnabled())
                .isTrue();
        assertThat(
                        ruleAutomationSettingsService
                                .update(firstTenantId, false)
                                .autoSendRulesEnabled())
                .isFalse();

        assertThat(ruleAutomationSettingsService.getOrCreate(firstTenantId).autoSendRulesEnabled())
                .isFalse();
        assertThat(ruleAutomationSettingsService.getOrCreate(secondTenantId).autoSendRulesEnabled())
                .isTrue();
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, displayName);
        return tenantId;
    }
}
