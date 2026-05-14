package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "billing_package")
public class BillingPackageEntity extends AbstractAuditableEntity {

    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    @Column(name = "credit_amount", nullable = false)
    private int creditAmount;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected BillingPackageEntity() {
        // Hibernate
    }

    public BillingPackageEntity(
            UUID id,
            String code,
            String name,
            long priceVnd,
            int creditAmount,
            String description,
            boolean active,
            int displayOrder) {
        super(id);
        this.code = code;
        this.name = name;
        this.priceVnd = priceVnd;
        this.creditAmount = creditAmount;
        this.description = description;
        this.active = active;
        this.displayOrder = displayOrder;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public long getPriceVnd() {
        return priceVnd;
    }

    public int getCreditAmount() {
        return creditAmount;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
