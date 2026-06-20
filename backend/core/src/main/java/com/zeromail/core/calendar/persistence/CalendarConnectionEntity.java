package com.zeromail.core.calendar.persistence;

import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Calendar OAuth connection row — one per workspace per Google account that has granted Calendar
 * scopes. Mirrors the {@code GmailConnectionEntity} column shape minus polling state (no {@code
 * isPrimary}, no watch fields, no {@code ingestionHealth}) because Calendar Phase 12 does not poll.
 *
 * <p><b>CAL-CONN-06 invariant:</b> NO {@code gmail_connection_id} column — Calendar is
 * workspace-shared, NOT mailbox-isolated. The schema invariant is locked by {@code
 * CalendarSchemaIsolationTest} (W0) and the cipher invariant by {@code
 * CalendarConnectionCipherTest} (this plan).
 *
 * <p><b>Crypto:</b> {@link #refreshTokenEncrypted} is the {@code OAuthTokenStore} envelope ({@code
 * [key_version:int32 | nonce:12 | ciphertext]}) — never a plaintext token. The field name is
 * intentionally distinct from {@code refreshToken} so the privacy log-content scanner
 * (CONVENTIONS.md §5) does not false-positive on this column declaration.
 */
@Entity
@Table(name = "calendar_connections")
public class CalendarConnectionEntity extends AbstractTenantOwnedEntity {

    @Column(name = "google_email", nullable = false)
    private String googleEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CalendarConnectionStatus status;

    /**
     * Encrypted refresh-token envelope written via {@code OAuthTokenStore.encrypt(..., tenantId,
     * RowDiscriminator.CALENDAR_CONNECTION)}. Plan W1 owns the encrypt call site; W2 owns the
     * decrypt + access-token refresh in {@code CalendarApiClientFactory}.
     */
    @Column(name = "refresh_token_encrypted")
    private byte[] refreshTokenEncrypted;

    @Column(name = "scopes_granted", columnDefinition = "text")
    private String scopesGranted;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "google_profile_name")
    private String googleProfileName;

    @Column(name = "google_profile_picture_url", columnDefinition = "text")
    private String googleProfilePictureUrl;

    protected CalendarConnectionEntity() {}

    public CalendarConnectionEntity(
            UUID id, UUID tenantId, String googleEmail, CalendarConnectionStatus status) {
        super(id, tenantId);
        this.googleEmail = googleEmail;
        this.status = status;
    }

    public String getGoogleEmail() {
        return googleEmail;
    }

    public CalendarConnectionStatus getStatus() {
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

    public Instant getDisconnectedAt() {
        return disconnectedAt;
    }

    public String getGoogleProfileName() {
        return googleProfileName;
    }

    public String getGoogleProfilePictureUrl() {
        return googleProfilePictureUrl;
    }

    public void setStatus(CalendarConnectionStatus status) {
        this.status = status;
    }

    public void setRefreshTokenEncrypted(byte[] envelope) {
        this.refreshTokenEncrypted = envelope;
    }

    public void setScopesGranted(String scopesGranted) {
        this.scopesGranted = scopesGranted;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    public void setDisconnectedAt(Instant disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }

    public void setGoogleProfileName(String googleProfileName) {
        this.googleProfileName = googleProfileName;
    }

    public void setGoogleProfilePictureUrl(String googleProfilePictureUrl) {
        this.googleProfilePictureUrl = googleProfilePictureUrl;
    }
}
