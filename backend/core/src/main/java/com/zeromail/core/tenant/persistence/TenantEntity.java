package com.zeromail.core.tenant.persistence;

import java.util.UUID;

import com.zeromail.core.shared.persistence.AbstractEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class TenantEntity extends AbstractEntity {

    @Column(name = "display_name", nullable = false)
    private String displayName;

    protected TenantEntity() {}

    public TenantEntity(UUID id, String displayName) {
        super(id);
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
