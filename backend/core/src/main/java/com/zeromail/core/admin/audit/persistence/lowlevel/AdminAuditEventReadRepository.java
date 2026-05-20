package com.zeromail.core.admin.audit.persistence.lowlevel;

import com.zeromail.core.admin.audit.projection.AdminAuditPageQuery;
import com.zeromail.core.admin.audit.projection.AdminAuditRow;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Raw JDBC read access to {@code admin_audit_event}. Lives in {@code persistence/lowlevel/} per
 * CONVENTIONS Section 2 — service classes in {@code usecases/} stay free of inline SQL.
 *
 * <p>Privacy: SELECT list is explicit; never includes {@code hash_curr} / {@code hash_prev} or
 * anything outside the projection.
 */
@Repository
public class AdminAuditEventReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminAuditEventReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public List<AdminAuditRow> findPage(AdminAuditPageQuery pageQuery) {
        Objects.requireNonNull(pageQuery, "pageQuery must not be null");
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

        return jdbcTemplate.query(
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
