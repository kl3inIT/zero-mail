package com.zeromail.core.triage.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenant_sender_opt_in")
public class TenantSenderOptInEntity extends AbstractTenantOwnedEntity {

    @Column(name = "sender_email", nullable = false, length = 320)
    private String senderEmail;

    protected TenantSenderOptInEntity() {
        // Hibernate
    }

    public TenantSenderOptInEntity(UUID id, UUID tenantId, String senderEmail) {
        super(id, tenantId);
        this.senderEmail = requireText(senderEmail, "senderEmail");
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }
}
