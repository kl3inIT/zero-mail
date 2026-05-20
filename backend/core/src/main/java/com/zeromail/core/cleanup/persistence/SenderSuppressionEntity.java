package com.zeromail.core.cleanup.persistence;

import com.zeromail.core.cleanup.domain.SuppressionReason;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Per-tenant sender suppression row (Phase 8, table {@code sender_suppression}, changelog 043).
 * Each row suppresses EITHER a specific {@code sender_email} OR a {@code sender_domain} — never
 * both. The DB enforces this via {@code ck_sender_suppression_one_target} CHECK; the entity
 * constructor mirrors the invariant at the application layer to fail loud before the DB rejects.
 *
 * <p>{@code reason} persists as lowercase ({@code 'manual'} / {@code 'replied'} / {@code 'auto'})
 * via {@link SuppressionReasonAttributeConverter} (auto-applied) — see that class for the D-C3
 * rationale.
 */
@Entity
@Table(name = "sender_suppression")
@AttributeOverride(name = "id", column = @Column(name = "id", nullable = false))
@SuppressWarnings("JpaDataSourceORMInspection")
public class SenderSuppressionEntity extends AbstractTenantOwnedEntity {

    @Column(name = "sender_email", length = 320)
    private String senderEmail;

    @Column(name = "sender_domain", length = 255)
    private String senderDomain;

    @Column(name = "reason", nullable = false, length = 16)
    private SuppressionReason reason;

    protected SenderSuppressionEntity() {
        // Hibernate
    }

    public SenderSuppressionEntity(
            UUID suppressionId,
            UUID tenantId,
            String senderEmail,
            String senderDomain,
            SuppressionReason reason) {
        super(suppressionId, tenantId);
        requireExactlyOneTarget(senderEmail, senderDomain);
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        this.senderEmail = senderEmail;
        this.senderDomain = senderDomain;
        this.reason = reason;
    }

    public UUID getSuppressionId() {
        return getId();
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public String getSenderDomain() {
        return senderDomain;
    }

    public SuppressionReason getReason() {
        return reason;
    }

    /**
     * Application-layer guard mirroring {@code ck_sender_suppression_one_target}: exactly one of
     * {@code senderEmail} / {@code senderDomain} must be non-null.
     */
    private static void requireExactlyOneTarget(String senderEmail, String senderDomain) {
        boolean emailPresent = senderEmail != null && !senderEmail.isBlank();
        boolean domainPresent = senderDomain != null && !senderDomain.isBlank();
        if (emailPresent == domainPresent) {
            throw new IllegalArgumentException(
                    "exactly one of senderEmail or senderDomain must be non-null (XOR)");
        }
    }
}
