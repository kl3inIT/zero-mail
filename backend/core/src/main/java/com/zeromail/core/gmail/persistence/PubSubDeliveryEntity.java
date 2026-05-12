package com.zeromail.core.gmail.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pubsub_delivery")
public class PubSubDeliveryEntity extends AbstractTenantOwnedEntity {

    @Column(name = "pubsub_message_id", nullable = false)
    private String pubsubMessageId;

    @Column(name = "history_id", nullable = false)
    private Long historyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected PubSubDeliveryEntity() {}

    public PubSubDeliveryEntity(
            UUID id, UUID tenantId, String pubsubMessageId, Long historyId, String payload) {
        super(id, tenantId);
        this.pubsubMessageId = pubsubMessageId;
        this.historyId = historyId;
        this.payload = payload;
    }

    public String getPubSubMessageId() {
        return pubsubMessageId;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setPubSubMessageId(String pubsubMessageId) {
        this.pubsubMessageId = pubsubMessageId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}
