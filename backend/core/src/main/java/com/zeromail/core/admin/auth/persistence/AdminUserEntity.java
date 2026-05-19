package com.zeromail.core.admin.auth.persistence;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "admin_users")
public class AdminUserEntity extends AbstractEntity {

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "user_handle", nullable = false, unique = true)
    private byte[] userHandle;

    @Convert(converter = AdminStatusAttributeConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private AdminStatus status;

    @Column(name = "credential_id", unique = true)
    private byte[] credentialId;

    @Column(name = "public_key_cose")
    private byte[] publicKeyCose;

    @Column(name = "attestation_object")
    private byte[] attestationObject;

    @Column(name = "attestation_client_data_json")
    private byte[] attestationClientDataJson;

    @Column(name = "signature_counter", nullable = false)
    private long signatureCounter;

    @Column(name = "aaguid")
    private UUID aaguid;

    @Column(name = "attestation_format", length = 50)
    private String attestationFormat;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 500)
    private String revokedReason;

    protected AdminUserEntity() {
        // Hibernate
    }

    public AdminUserEntity(
            UUID id, String email, String displayName, byte[] userHandle, AdminStatus status) {
        super(id);
        this.email = email;
        this.displayName = displayName;
        this.userHandle = copy(userHandle);
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public byte[] getUserHandle() {
        return copy(userHandle);
    }

    public AdminStatus getStatus() {
        return status;
    }

    public byte[] getCredentialId() {
        return copy(credentialId);
    }

    public byte[] getPublicKeyCose() {
        return copy(publicKeyCose);
    }

    public byte[] getAttestationObject() {
        return copy(attestationObject);
    }

    public byte[] getAttestationClientDataJson() {
        return copy(attestationClientDataJson);
    }

    public long getSignatureCounter() {
        return signatureCounter;
    }

    public UUID getAaguid() {
        return aaguid;
    }

    public String getAttestationFormat() {
        return attestationFormat;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void activate(
            byte[] newCredentialId,
            byte[] newPublicKeyCose,
            byte[] newAttestationObject,
            byte[] newAttestationClientDataJson,
            long newSignatureCounter,
            UUID newAaguid,
            String newAttestationFormat) {
        status = AdminStatus.ACTIVE;
        credentialId = copy(newCredentialId);
        publicKeyCose = copy(newPublicKeyCose);
        attestationObject = copy(newAttestationObject);
        attestationClientDataJson = copy(newAttestationClientDataJson);
        signatureCounter = newSignatureCounter;
        aaguid = newAaguid;
        attestationFormat = newAttestationFormat;
    }

    public void revoke(Instant revokedAt, String revokedReason) {
        status = AdminStatus.REVOKED;
        this.revokedAt = revokedAt;
        this.revokedReason = revokedReason;
    }

    private static byte[] copy(byte[] bytes) {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }
}
