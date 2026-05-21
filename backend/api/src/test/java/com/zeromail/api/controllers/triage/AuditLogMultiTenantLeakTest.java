package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.triage.persistence.lowlevel.AuditLogReadRepository;
import com.zeromail.core.triage.projection.AuditLogPageQuery;
import com.zeromail.core.triage.usecases.AuditLogQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AuditLogMultiTenantLeakTest {

    @Test
    void audit_log_query_filters_every_page_by_current_tenant() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        AuditLogReadRepository auditLogReadRepository = new AuditLogReadRepository(jdbcTemplate);
        AuditLogQueryService auditLogQueryService =
                new AuditLogQueryService(auditLogReadRepository);
        UUID tenantId = UUID.randomUUID();

        auditLogQueryService.page(tenantId, new AuditLogPageQuery(50, null, null, null, null));

        assertThat(jdbcTemplate.sql()).contains("where tenant_id = ?");
        assertThat(jdbcTemplate.parameters()).isNotEmpty();
        assertThat(jdbcTemplate.parameters()[0]).isEqualTo(tenantId);
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {

        private String sql;
        private Object[] parameters = new Object[0];

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... parameters) {
            this.sql = sql;
            this.parameters = parameters;
            return List.of();
        }

        String sql() {
            return sql;
        }

        Object[] parameters() {
            return parameters;
        }
    }
}
