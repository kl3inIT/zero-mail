package com.zeromail.core.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "gmail_connections")
public class GmailConnectionEntity {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "google_email", nullable = false)
    private String googleEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GmailConnectionStatus status;

    /**
     * Encrypted refresh-token envelope: [key_version:int32 | nonce:12 | ciphertext:variable].
     * Plan 06 owns the cipher; this column never stores a plaintext token. The deny-list
     * regex matches `refreshToken`, not `refreshTokenEncrypted`, so this field name is
     * intentionally distinct.
     */
    @Column(name = "refresh_token_encrypted")
    private byte[] refreshTokenEncrypted;

    @Column(name = "scopes_granted", columnDefinition = "text")
    private String scopesGranted;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    protected GmailConnectionEntity() {}

    public GmailConnectionEntity(UUID id, UUID tenantId, String googleEmail, GmailConnectionStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.googleEmail = googleEmail;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getGoogleEmail() { return googleEmail; }
    public GmailConnectionStatus getStatus() { return status; }
    public byte[] getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public String getScopesGranted() { return scopesGranted; }
    public Instant getConnectedAt() { return connectedAt; }
    public Instant getDisconnectedAt() { return disconnectedAt; }

    public void setStatus(GmailConnectionStatus status) { this.status = status; }
    public void setRefreshTokenEncrypted(byte[] envelope) { this.refreshTokenEncrypted = envelope; }
    public void setScopesGranted(String scopes) { this.scopesGranted = scopes; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
    public void setDisconnectedAt(Instant disconnectedAt) { this.disconnectedAt = disconnectedAt; }
}
