package com.zeromail.core.gmail.persistence;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.domain.GmailIngestionHealth;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gmail_connections")
public class GmailConnectionEntity extends AbstractTenantOwnedEntity {

    @Column(name = "google_email", nullable = false)
    private String googleEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GmailConnectionStatus status;

    /**
     * Encrypted refresh-token envelope: [key_version:int32 | nonce:12 | ciphertext:variable]. Plan
     * 06 owns the cipher; this column never stores a plaintext token. The deny-list regex matches
     * `refreshToken`, not `refreshTokenEncrypted`, so this field name is intentionally distinct.
     */
    @Column(name = "refresh_token_encrypted")
    private byte[] refreshTokenEncrypted;

    @Column(name = "scopes_granted", columnDefinition = "text")
    private String scopesGranted;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @Column(name = "display_purpose")
    private String displayPurpose;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "last_synced_history_id")
    private Long lastSyncedHistoryId;

    @Column(name = "watch_history_id")
    private Long watchHistoryId;

    @Column(name = "watch_expires_at")
    private Instant watchExpiresAt;

    @Column(name = "watch_renewed_at")
    private Instant watchRenewedAt;

    @Column(name = "watch_consecutive_failures", nullable = false)
    private int watchConsecutiveFailures = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_health", nullable = false)
    private GmailIngestionHealth ingestionHealth = GmailIngestionHealth.HEALTHY;

    protected GmailConnectionEntity() {}

    public GmailConnectionEntity(
            UUID id, UUID tenantId, String googleEmail, GmailConnectionStatus status) {
        super(id, tenantId);
        this.googleEmail = googleEmail;
        this.status = status;
    }

    public String getGoogleEmail() {
        return googleEmail;
    }

    public GmailConnectionStatus getStatus() {
        return status;
    }

    public byte[] getRefreshTokenEncrypted() {
        return refreshTokenEncrypted;
    }

    public String getScopesGranted() {
        return scopesGranted;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public String getDisplayPurpose() {
        return displayPurpose;
    }

    public Instant getDisconnectedAt() {
        return disconnectedAt;
    }

    public Long getLastSyncedHistoryId() {
        return lastSyncedHistoryId;
    }

    public Long getWatchHistoryId() {
        return watchHistoryId;
    }

    public Instant getWatchExpiresAt() {
        return watchExpiresAt;
    }

    public Instant getWatchRenewedAt() {
        return watchRenewedAt;
    }

    public int getWatchConsecutiveFailures() {
        return watchConsecutiveFailures;
    }

    public GmailIngestionHealth getIngestionHealth() {
        return ingestionHealth;
    }

    public void setStatus(GmailConnectionStatus status) {
        this.status = status;
    }

    public void setRefreshTokenEncrypted(byte[] envelope) {
        this.refreshTokenEncrypted = envelope;
    }

    public void setScopesGranted(String scopes) {
        this.scopesGranted = scopes;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    public void setDisplayPurpose(String displayPurpose) {
        this.displayPurpose = displayPurpose;
    }

    public void setDisconnectedAt(Instant disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }

    public void setLastSyncedHistoryId(Long lastSyncedHistoryId) {
        this.lastSyncedHistoryId = lastSyncedHistoryId;
    }

    public void setWatchHistoryId(Long watchHistoryId) {
        this.watchHistoryId = watchHistoryId;
    }

    public void setWatchExpiresAt(Instant watchExpiresAt) {
        this.watchExpiresAt = watchExpiresAt;
    }

    public void setWatchRenewedAt(Instant watchRenewedAt) {
        this.watchRenewedAt = watchRenewedAt;
    }

    public void setWatchConsecutiveFailures(int watchConsecutiveFailures) {
        this.watchConsecutiveFailures = watchConsecutiveFailures;
    }

    public void setIngestionHealth(GmailIngestionHealth ingestionHealth) {
        this.ingestionHealth = ingestionHealth;
    }
}
