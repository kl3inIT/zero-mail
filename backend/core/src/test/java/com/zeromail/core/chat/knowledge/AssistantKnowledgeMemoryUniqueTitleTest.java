package com.zeromail.core.chat.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AssistantKnowledgeMemoryUniqueTitleTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void tenantScopedKnowledgeTitlesAreUnique() {
        UUID tenantId = UUID.randomUUID();
        insertTenant(tenantId, "knowledge-unique");
        insertSnippet(tenantId, "Same title", "first content");

        assertThatThrownBy(() -> insertSnippet(tenantId, "Same title", "second content"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameKnowledgeTitleIsAllowedAcrossTenants() {
        UUID firstTenantId = UUID.randomUUID();
        UUID secondTenantId = UUID.randomUUID();
        insertTenant(firstTenantId, "knowledge-first");
        insertTenant(secondTenantId, "knowledge-second");

        insertSnippet(firstTenantId, "Shared title", "first content");
        insertSnippet(secondTenantId, "Shared title", "second content");

        Integer rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from assistant_knowledge_snippet where title = ?",
                        Integer.class,
                        "Shared title");
        assertThat(rowCount).isEqualTo(2);
    }

    private void insertTenant(UUID tenantId, String displayName) {
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
    }

    private void insertSnippet(UUID tenantId, String title, String content) {
        jdbcTemplate.update(
                """
                insert into assistant_knowledge_snippet(id, tenant_id, title, content)
                values (?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                title,
                content);
    }
}
