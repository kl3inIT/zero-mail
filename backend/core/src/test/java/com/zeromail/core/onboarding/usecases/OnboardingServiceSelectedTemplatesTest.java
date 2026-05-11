package com.zeromail.core.onboarding.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class OnboardingServiceSelectedTemplatesTest extends PostgresContainerTest {

    @Autowired OnboardingService onboardingService;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void selected_enabled_template_keys_are_distinct_stable_and_tenant_scoped() {
        UUID tenantId = seedTenant("onboarding-selected-templates");
        UUID otherTenantId = seedTenant("onboarding-selected-templates-other");
        insertSelection(tenantId, "label-newsletters", true);
        insertSelection(tenantId, "archive-receipts", true);
        insertSelection(tenantId, "pin-calendar", false);
        insertSelection(otherTenantId, "pin-calendar", true);

        assertThat(onboardingService.selectedEnabledTemplateKeys(tenantId))
                .containsExactly("archive-receipts", "label-newsletters");
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
        return tenantId;
    }

    private void insertSelection(UUID tenantId, String templateKey, boolean enabled) {
        jdbcTemplate.update(
                "insert into onboarding_selections(id, tenant_id, template_key, enabled) values (?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                templateKey,
                enabled);
    }
}
