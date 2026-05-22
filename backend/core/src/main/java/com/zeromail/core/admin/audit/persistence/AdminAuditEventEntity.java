package com.zeromail.core.admin.audit.persistence;

import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin_audit_event")
@SuppressWarnings("JpaDataSourceORMInspection")
public class AdminAuditEventEntity extends AbstractEntity {

    @Column(name = "chain_index", nullable = false, insertable = false, updatable = false)
    private long chainIndex;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "actor_email", nullable = false, length = 320)
    private String actorEmail;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "target_kind", length = 32)
    private String targetKind;

    @Column(name = "target_id")
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state_json", columnDefinition = "jsonb")
    private String beforeStateJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state_json", columnDefinition = "jsonb")
    private String afterStateJson;

    @Column(name = "reason", length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "request_ip", columnDefinition = "inet")
    private String requestIp;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "canonical_timestamp_ms", nullable = false)
    private long canonicalTimestampMs;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "hmac_chain_hash", nullable = false)
    private byte[] hmacChainHash;

    protected AdminAuditEventEntity() {
        // Hibernate
    }

    public long getChainIndex() {
        return chainIndex;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getAction() {
        return action;
    }

    public String getTargetKind() {
        return targetKind;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getBeforeStateJson() {
        return beforeStateJson;
    }

    public String getAfterStateJson() {
        return afterStateJson;
    }

    public String getReason() {
        return reason;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public long getCanonicalTimestampMs() {
        return canonicalTimestampMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public byte[] getHmacChainHash() {
        return Arrays.copyOf(hmacChainHash, hmacChainHash.length);
    }
}
