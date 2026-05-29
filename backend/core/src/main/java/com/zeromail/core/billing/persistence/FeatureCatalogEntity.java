package com.zeromail.core.billing.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Catalog row describing one billable LLM-calling feature. The {@code code} column mirrors a value
 * of {@link com.zeromail.core.billing.domain.CallSite} and is the natural primary key (no synthetic
 * UUID id) so cross-table joins via {@code feature_code} stay readable in SQL.
 *
 * <p>{@code defaultCreditCost} replaces the formerly hard-coded {@code CallSite#cost()} field —
 * operators can tune cost from the admin UI without a redeploy.
 *
 * <p>A {@code FeatureCatalogConsistencyChecker} startup validator enforces that every {@link
 * com.zeromail.core.billing.domain.CallSite} enum value has a row in this table.
 */
@Entity
@Table(name = "feature_catalog")
@EntityListeners(AuditingEntityListener.class)
public class FeatureCatalogEntity {

    @Id
    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "default_credit_cost", nullable = false)
    private int defaultCreditCost;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected FeatureCatalogEntity() {
        // Hibernate
    }

    public FeatureCatalogEntity(
            String code,
            String displayName,
            String description,
            String category,
            int defaultCreditCost,
            boolean active,
            int sortOrder) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.defaultCreditCost = defaultCreditCost;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public int getDefaultCreditCost() {
        return defaultCreditCost;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
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

    public void updateCost(int defaultCreditCost) {
        this.defaultCreditCost = defaultCreditCost;
    }

    public void updateActive(boolean active) {
        this.active = active;
    }

    public void updateDisplay(String displayName, String description, int sortOrder) {
        this.displayName = displayName;
        this.description = description;
        this.sortOrder = sortOrder;
    }
}
