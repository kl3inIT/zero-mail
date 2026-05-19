package com.zeromail.core.admin.audit.usecases;

import com.zeromail.core.admin.audit.projection.AdminAuditPage;
import com.zeromail.core.admin.audit.projection.AdminAuditPageQuery;
import com.zeromail.core.admin.audit.projection.AdminAuditRow;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditQueryService {

    private final JdbcTemplate jdbcTemplate;

    public AdminAuditQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Transactional(readOnly = true)
    public AdminAuditPage page(AdminAuditPageQuery query) {
        AdminAuditPageQuery pageQuery = Objects.requireNonNull(query, "query must not be null");
        ArrayList<Object> parameters = new ArrayList<>();
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT id, chain_index, actor_email, action, target_kind, target_id,
                               reason, host(request_ip) AS request_ip_text, request_id, created_at
                        FROM admin_audit_event
                        WHERE 1 = 1
                        """);
        appendFilters(sql, parameters, pageQuery);
        sql.append(" ORDER BY created_at DESC, chain_index DESC LIMIT ? OFFSET ?");
        parameters.add(pageQuery.limit() + 1);
        parameters.add(pageQuery.offset());

        List<AdminAuditRow> rows =
                jdbcTemplate.query(
                        sql.toString(),
                        (resultSet, _) ->
                                new AdminAuditRow(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getLong("chain_index"),
                                        resultSet.getString("actor_email"),
                                        resultSet.getString("action"),
                                        resultSet.getString("target_kind"),
                                        resultSet.getObject("target_id", UUID.class),
                                        resultSet.getString("reason"),
                                        resultSet.getString("request_ip_text"),
                                        resultSet.getObject("request_id", UUID.class),
                                        resultSet.getTimestamp("created_at").toInstant()),
                        parameters.toArray());
        boolean hasNextPage = rows.size() > pageQuery.limit();
        List<AdminAuditRow> pageItems = hasNextPage ? rows.subList(0, pageQuery.limit()) : rows;
        return new AdminAuditPage(pageItems, hasNextPage);
    }

    private static void appendFilters(
            StringBuilder sql, ArrayList<Object> parameters, AdminAuditPageQuery pageQuery) {
        if (pageQuery.actorEmail() != null) {
            sql.append(" AND actor_email = ?");
            parameters.add(pageQuery.actorEmail());
        }
        if (pageQuery.action() != null) {
            sql.append(" AND action = ?");
            parameters.add(pageQuery.action());
        }
        if (pageQuery.targetKind() != null) {
            sql.append(" AND target_kind = ?");
            parameters.add(pageQuery.targetKind());
        }
        if (pageQuery.targetId() != null) {
            sql.append(" AND target_id = ?");
            parameters.add(pageQuery.targetId());
        }
        if (pageQuery.since() != null) {
            sql.append(" AND created_at >= ?");
            parameters.add(Timestamp.from(pageQuery.since()));
        }
        if (pageQuery.until() != null) {
            sql.append(" AND created_at < ?");
            parameters.add(Timestamp.from(pageQuery.until()));
        }
    }
}
