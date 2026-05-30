package com.zeromail.core.billing.persistence;

import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "billing_checkout_session")
public class BillingCheckoutSessionEntity extends AbstractEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false, length = 64, updatable = false)
    private String planCode;

    @Column(name = "user_email", length = 320, updatable = false)
    private String userEmail;

    @Column(name = "provider_checkout_id", length = 255, updatable = false)
    private String providerCheckoutId;

    @Column(name = "checkout_url", columnDefinition = "text", updatable = false)
    private String checkoutUrl;

    @Column(name = "status", nullable = false, length = 32, updatable = false)
    private String status;

    @Column(name = "failure_reason", columnDefinition = "text", updatable = false)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reuse_expires_at", nullable = false, updatable = false)
    private Instant reuseExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_jsonb", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String requestJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_jsonb", updatable = false, columnDefinition = "jsonb")
    private String responseJsonb;

    protected BillingCheckoutSessionEntity() {
        // Hibernate
    }

    public BillingCheckoutSessionEntity(
            UUID id,
            UUID tenantId,
            String planCode,
            String userEmail,
            String providerCheckoutId,
            String checkoutUrl,
            String status,
            String failureReason,
            Instant createdAt,
            Instant reuseExpiresAt,
            String requestJsonb,
            String responseJsonb) {
        super(id);
        this.tenantId = tenantId;
        this.planCode = planCode;
        this.userEmail = userEmail;
        this.providerCheckoutId = providerCheckoutId;
        this.checkoutUrl = checkoutUrl;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.reuseExpiresAt = reuseExpiresAt;
        this.requestJsonb = requestJsonb;
        this.responseJsonb = responseJsonb;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getProviderCheckoutId() {
        return providerCheckoutId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReuseExpiresAt() {
        return reuseExpiresAt;
    }

    public String getRequestJsonb() {
        return requestJsonb;
    }

    public String getResponseJsonb() {
        return responseJsonb;
    }
}
