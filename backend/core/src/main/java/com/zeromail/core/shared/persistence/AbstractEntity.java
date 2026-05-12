package com.zeromail.core.shared.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/**
 * Tier 1: identity-only base for every JPA entity in {@code com.zeromail.core}. Hoists the {@code
 * UUID id} {@code @Id} field that every entity in the project carries.
 *
 * <p><b>Implements decision D-A1</b> (3-tier hierarchy). {@code TenantEntity} extends THIS class
 * directly (a tenant is not tenant-owned — it IS the tenant). Auditable + tenant-owned bases extend
 * this one.
 *
 * <p>Adapted from JHipster {@code AbstractAuditingEntity<T>} (single class, generic ID, includes
 * {@code createdBy}/{@code lastModifiedBy}) but split into 3 tiers and dropped identity columns per
 * privacy constraint (D-A4). Adapted from Jmix {@code EnumClass<T>} for enum side (see {@code
 * core.shared.lang}).
 */
@MappedSuperclass
public abstract class AbstractEntity {

    @Id private UUID id;

    protected AbstractEntity() {}

    protected AbstractEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
