package com.zeromail.core.admin.audit.persistence;

import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_read_event")
public class AdminReadEventEntity extends AbstractEntity {

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "actor_email", length = 320)
    private String actorEmail;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "target_kind", length = 32)
    private String targetKind;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AdminReadEventEntity() {
        // Hibernate
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
