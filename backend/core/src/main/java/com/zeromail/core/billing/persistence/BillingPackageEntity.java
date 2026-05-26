package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "included_features", columnDefinition = "text[]", nullable = false)
    private String[] includedFeatures;

    @Column(name = "featured", nullable = false)
    private boolean featured;

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
            String[] includedFeatures,
            boolean featured,
            boolean active,
            int displayOrder) {
        super(id);
        this.code = code;
        this.name = name;
        this.priceVnd = priceVnd;
        this.creditAmount = creditAmount;
        this.description = description;
        this.includedFeatures = copyIncludedFeatures(includedFeatures);
        this.featured = featured;
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

    public String[] getIncludedFeatures() {
        return copyIncludedFeatures(includedFeatures);
    }

    public boolean isFeatured() {
        return featured;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void updateDetails(
            String name,
            long priceVnd,
            int creditAmount,
            String description,
            String[] includedFeatures,
            boolean featured,
            boolean active,
            int displayOrder) {
        this.name = name;
        this.priceVnd = priceVnd;
        this.creditAmount = creditAmount;
        this.description = description;
        this.includedFeatures = copyIncludedFeatures(includedFeatures);
        this.featured = featured;
        this.active = active;
        this.displayOrder = displayOrder;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    private static String[] copyIncludedFeatures(String[] includedFeatures) {
        if (includedFeatures == null) {
            return new String[0];
        }
        return Arrays.copyOf(includedFeatures, includedFeatures.length);
    }
}
