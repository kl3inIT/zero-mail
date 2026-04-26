package com.zeromail.core.shared.persistence;

import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Tier 3: identity + audit + multi-tenant ownership. Extends {@link AbstractAuditableEntity}.
 *
 * <p>Hoists the {@code @TenantId @Column("tenant_id") private UUID tenantId} block that
 * appears verbatim on {@code UserEntity}, {@code OnboardingSelectionEntity}, and
 * {@code GmailConnectionEntity}.
 *
 * <p><b>Implements decision D-A1.</b> Hibernate 7's {@code @TenantId} discriminator filter
 * binds to the field carrying the annotation regardless of declaring class — verified via
 * {@code MultiTenantLeakIntegrationTest} (FND-05). Concrete entities only declare their
 * domain-specific columns.
 */
@MappedSuperclass
public abstract class AbstractTenantOwnedEntity extends AbstractAuditableEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    protected AbstractTenantOwnedEntity() {}

    protected AbstractTenantOwnedEntity(UUID id, UUID tenantId) {
        super(id);
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
