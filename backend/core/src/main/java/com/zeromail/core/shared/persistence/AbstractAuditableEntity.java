package com.zeromail.core.shared.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Tier 2: identity + audit timestamps + optimistic-lock version. Extends {@link AbstractEntity}.
 *
 * <p><b>Implements decisions D-A2 (audit field naming), D-A3 (schema additions), D-A4
 * (auditing mechanism via Spring Data + {@link AuditingEntityListener} + the API module's
 * {@code JpaAuditingConfig}).</b>
 *
 * <p><b>D-A5 caveat — bulk SQL bypass:</b> {@code @LastModifiedDate} and {@code @PreUpdate}
 * lifecycle callbacks DO NOT fire for bulk JPQL {@code @Modifying @Query}, native SQL via
 * {@code entityManager.createNativeQuery(...)}, or {@code JdbcTemplate} updates. If a future
 * bulk update is needed, set {@code updated_at = NOW()} explicitly in the query, or migrate
 * to a DB trigger for that specific table. This is documented again in
 * {@code core.shared.persistence/package-info.java}.
 *
 * <p><b>Privacy (D-A4):</b> {@code @CreatedBy} / {@code @LastModifiedBy} are deliberately
 * omitted. Row-level user identity is forbidden by the project privacy constraint until the
 * Phase 6 audit-log table lands with a proper {@code AuditorAware<UUID>} bean.
 */
@SuppressWarnings("unused")
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAuditableEntity extends AbstractEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected AbstractAuditableEntity() {}

    protected AbstractAuditableEntity(UUID id) {
        super(id);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Integer getVersion() {
        return version;
    }
}
