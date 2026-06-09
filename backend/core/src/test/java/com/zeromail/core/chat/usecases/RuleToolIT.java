package com.zeromail.core.chat.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.usecases.tools.GetRuleToolHandler;
import com.zeromail.core.chat.usecases.tools.ListRulesToolHandler;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class RuleToolIT extends PostgresContainerTest {

    @Autowired ListRulesToolHandler listRulesToolHandler;
    @Autowired GetRuleToolHandler getRuleToolHandler;
    @Autowired RuleRepository ruleRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void rule_read_tools_route_through_rules_service_and_return_v1_rule_fields() {
        UUID tenantId = seedTenant("rule-tool-tenant");
        UUID otherTenantId = seedTenant("rule-tool-other-tenant");
        UUID ruleId = UUID.randomUUID();
        UUID otherRuleId = UUID.randomUUID();
        seedRule(tenantId, ruleId, "Archive VIP updates");
        seedRule(otherTenantId, otherRuleId, "Other tenant rule");

        String listJson =
                withTenant(
                        tenantId,
                        () -> listRulesToolHandler.executeJson("{}", tenantId.toString()));
        String detailJson =
                withTenant(
                        tenantId,
                        () ->
                                getRuleToolHandler.executeJson(
                                        "{\"ruleId\":\"" + ruleId + "\"}", tenantId.toString()));

        assertThat(listJson)
                .contains(ruleId.toString())
                .contains("\"displayName\":\"Archive VIP updates\"")
                .contains("\"matcher\":\"")
                .contains("\\\"schemaVersion\\\": \\\"rules.v1\\\"")
                .contains("\"actions\":\"[{\\\"type\\\": \\\"archive\\\"}]\"")
                .doesNotContain(otherRuleId.toString())
                .doesNotContain("Other tenant rule");
        assertThat(detailJson)
                .contains(ruleId.toString())
                .contains("\"sourceLanguage\":\"en\"")
                .contains("\"schemaVersion\":\"rules.v1\"")
                .contains("\"enabled\":true");
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        UUID gmailConnectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
        jdbcTemplate.update(
                "insert into gmail_connections(id, tenant_id, google_email, status, is_primary) values (?, ?, ?, 'CONNECTED', true)",
                gmailConnectionId,
                tenantId,
                displayName + "@example.test");
        return tenantId;
    }

    private UUID primaryGmailConnectionId(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "select id from gmail_connections where tenant_id = ? and is_primary = true",
                UUID.class,
                tenantId);
    }

    private void seedRule(UUID tenantId, UUID ruleId, String displayName) {
        withTenant(
                tenantId,
                () -> {
                    RuleEntity rule =
                            new RuleEntity(
                                    ruleId,
                                    tenantId,
                                    primaryGmailConnectionId(tenantId),
                                    displayName,
                                    "Archive updates from VIP senders",
                                    RuleLanguage.EN,
                                    RuleSchemaVersion.RULES_V1,
                                    "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"example.test\"}",
                                    "[{\"type\":\"archive\"}]",
                                    10,
                                    null,
                                    null);
                    rule.setEnabled(true);
                    ruleRepository.saveAndFlush(rule);
                    return null;
                });
    }

    private static <T> T withTenant(UUID tenantId, TenantCallable<T> tenantCallable) {
        try {
            return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                    .call(tenantCallable::call);
        } catch (Exception exception) {
            throw new IllegalStateException("tenant-scoped test action failed", exception);
        }
    }

    @FunctionalInterface
    private interface TenantCallable<T> {
        T call() throws Exception;
    }
}
