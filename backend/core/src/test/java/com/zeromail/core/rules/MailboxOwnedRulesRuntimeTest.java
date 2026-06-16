package com.zeromail.core.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RED contract for AUTO-04 mailbox-owned rules.
 *
 * <p>Waits on schema column {@code rules.gmail_connection_id}, unique index {@code
 * uq_rules_tenant_template_key_present} widened by mailbox, and a runtime repository load method
 * that filters by tenant, source mailbox, and enabled=true. The future method is reached by
 * reflection so this test compiles before the method exists.
 */
class MailboxOwnedRulesRuntimeTest extends PostgresContainerTest {

    private static final String RULE_REPOSITORY_FQN =
            "com.zeromail.core.rules.persistence.RuleRepository";

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void rulesTableOwnsRulesByMailbox() {
        assertThat(columnNullability("rules", "gmail_connection_id"))
                .as("rules.gmail_connection_id must exist after backfill and be NOT NULL")
                .containsExactly("NO");
    }

    @Test
    void defaultTemplateUniquenessIsMailboxScoped() {
        assertThat(indexDefinition("uq_rules_tenant_template_key_present"))
                .contains("tenant_id")
                .contains("gmail_connection_id")
                .contains("template_key")
                .contains("WHERE");
    }

    @Test
    void runtimeRuleLoadRequiresTenantAndSourceMailbox() throws Exception {
        Class<?> ruleRepositoryClass = Class.forName(RULE_REPOSITORY_FQN);

        Method mailboxScopedLoadMethod =
                assertFutureMethodPresent(
                        ruleRepositoryClass,
                        "findEnabledByTenantIdAndGmailConnectionIdOrderByOrderIndex",
                        UUID.class,
                        UUID.class);

        assertThat(mailboxScopedLoadMethod.getReturnType()).isEqualTo(List.class);
    }

    private List<String> columnNullability(String tableName, String columnName) {
        return jdbcTemplate.queryForList(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """,
                String.class,
                tableName,
                columnName);
    }

    private String indexDefinition(String indexName) {
        List<String> indexDefinitions =
                jdbcTemplate.queryForList(
                        """
                        SELECT indexdef
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = ?
                        """,
                        String.class,
                        indexName);
        assertThat(indexDefinitions).as(indexName + " must exist").hasSize(1);
        return indexDefinitions.getFirst();
    }

    private static Method assertFutureMethodPresent(
            Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        try {
            return targetClass.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException missingMethod) {
            throw new AssertionError(
                    "Expected future mailbox-scoped rules load method "
                            + targetClass.getName()
                            + "."
                            + methodName
                            + " to exist",
                    missingMethod);
        }
    }
}
