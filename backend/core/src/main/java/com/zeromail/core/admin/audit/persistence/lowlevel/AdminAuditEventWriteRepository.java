package com.zeromail.core.admin.audit.persistence.lowlevel;

import com.zeromail.core.admin.audit.usecases.HmacChainHasher;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Write + chain-read access to {@code admin_audit_event} and {@code admin_read_event}. Centralises
 * the schema knowledge previously duplicated between {@code AdminAuditWriter} (writes) and {@code
 * AdminAuditChainVerifier} (chain reads).
 */
@Repository
public class AdminAuditEventWriteRepository {

    private static final long AUDIT_CHAIN_ADVISORY_LOCK_ID = 8_001_001L;
    private static final String CHAIN_INDEX_SEQUENCE = "admin_audit_event_chain_index_seq";

    private final JdbcTemplate jdbcTemplate;

    public AdminAuditEventWriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public void acquireChainAdvisoryLock() {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?)", _ -> {}, AUDIT_CHAIN_ADVISORY_LOCK_ID);
    }

    public long reserveNextChainIndex() {
        Long chainIndex =
                jdbcTemplate.queryForObject(
                        "SELECT nextval('" + CHAIN_INDEX_SEQUENCE + "')", Long.class);
        if (chainIndex == null) {
            throw new IllegalStateException("Unable to reserve admin audit chain index");
        }
        return chainIndex;
    }

    public String canonicalJson(String jsonValue) {
        if (jsonValue == null) {
            return null;
        }
        return jdbcTemplate.queryForObject(
                "SELECT CAST(? AS jsonb)::text", String.class, jsonValue);
    }

    public void insertAuditEvent(
            UUID auditId,
            long chainIndex,
            UUID actorUserId,
            String actorEmail,
            String actionId,
            String targetKind,
            UUID targetId,
            String canonicalBeforeStateJson,
            String canonicalAfterStateJson,
            String reason,
            String requestIp,
            UUID requestId,
            long canonicalTimestampMs,
            byte[] hmacChainHash) {
        jdbcTemplate.update(
                """
                INSERT INTO admin_audit_event(
                    id, chain_index, actor_user_id, actor_email, action, target_kind, target_id,
                    before_state_json, after_state_json, reason, request_ip, request_id,
                    canonical_timestamp_ms, hmac_chain_hash
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, CAST(? AS inet), ?, ?, ?)
                """,
                auditId,
                chainIndex,
                actorUserId,
                actorEmail,
                actionId,
                targetKind,
                targetId,
                canonicalBeforeStateJson,
                canonicalAfterStateJson,
                reason,
                requestIp,
                requestId,
                canonicalTimestampMs,
                hmacChainHash);
    }

    public void insertReadEvent(
            UUID readEventId,
            UUID actorUserId,
            String actorEmail,
            String action,
            String targetKind,
            UUID targetId) {
        jdbcTemplate.update(
                """
                INSERT INTO admin_read_event(
                    id, actor_user_id, actor_email, action, target_kind, target_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                readEventId,
                actorUserId,
                actorEmail,
                action,
                targetKind,
                targetId);
    }

    public List<HmacChainHasher.AuditChainEntry> findAllEntriesInChainOrder() {
        return jdbcTemplate.query(
                """
                SELECT chain_index, actor_user_id, actor_email, action, target_kind,
                       target_id, before_state_json::text AS before_state_json_text,
                       after_state_json::text AS after_state_json_text, reason,
                       host(request_ip) AS request_ip_text, request_id,
                       canonical_timestamp_ms, hmac_chain_hash
                FROM admin_audit_event
                ORDER BY chain_index ASC
                """,
                (resultSet, _) ->
                        new HmacChainHasher.AuditChainEntry(
                                resultSet.getLong("chain_index"),
                                resultSet.getObject("actor_user_id", UUID.class),
                                resultSet.getString("actor_email"),
                                resultSet.getString("action"),
                                resultSet.getString("target_kind"),
                                resultSet.getObject("target_id", UUID.class),
                                resultSet.getString("before_state_json_text"),
                                resultSet.getString("after_state_json_text"),
                                resultSet.getString("reason"),
                                resultSet.getString("request_ip_text"),
                                resultSet.getObject("request_id", UUID.class),
                                resultSet.getLong("canonical_timestamp_ms"),
                                resultSet.getBytes("hmac_chain_hash")));
    }
}
