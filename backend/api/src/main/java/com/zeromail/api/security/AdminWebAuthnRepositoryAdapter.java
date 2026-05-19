package com.zeromail.api.security;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.exception.AdminAuthException;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import com.zeromail.core.admin.auth.usecases.WebAuthnCredentialStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminWebAuthnRepositoryAdapter
        implements PublicKeyCredentialUserEntityRepository, UserCredentialRepository {

    private static final String DEFAULT_CREDENTIAL_LABEL = "Zero Mail Admin Passkey";

    private final AdminUserRepository adminUserRepository;
    private final WebAuthnCredentialStore webAuthnCredentialStore;
    private final Clock clock;

    public AdminWebAuthnRepositoryAdapter(
            AdminUserRepository adminUserRepository,
            WebAuthnCredentialStore webAuthnCredentialStore,
            Clock clock) {
        this.adminUserRepository = adminUserRepository;
        this.webAuthnCredentialStore = webAuthnCredentialStore;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        return adminUserRepository
                .findByUserHandle(id.getBytes())
                .map(this::toUserEntity)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        return adminUserRepository
                .findByEmailIgnoreCase(username)
                .map(this::toUserEntity)
                .orElse(null);
    }

    @Override
    @Transactional
    public void save(PublicKeyCredentialUserEntity userEntity) {
        adminUserRepository
                .findByEmailIgnoreCase(userEntity.getName())
                .orElseGet(
                        () ->
                                adminUserRepository.save(
                                        new AdminUserEntity(
                                                UUID.randomUUID(),
                                                userEntity.getName(),
                                                userEntity.getDisplayName(),
                                                userEntity.getId().getBytes(),
                                                AdminStatus.PENDING_ENROLLMENT)));
    }

    @Override
    public void delete(Bytes id) {
        // Credential deletion is denied by Spring Security's default authorization manager.
    }

    @Override
    @Transactional
    public void save(CredentialRecord credentialRecord) {
        AdminUserEntity adminUser =
                adminUserRepository
                        .findByUserHandle(credentialRecord.getUserEntityUserId().getBytes())
                        .orElseThrow(() -> new AdminAuthException("Admin user handle not found"));
        boolean newCredential =
                adminUser.getCredentialId() == null
                        || !Arrays.equals(
                                adminUser.getCredentialId(),
                                credentialRecord.getCredentialId().getBytes());
        if (newCredential) {
            webAuthnCredentialStore.saveCredential(
                    adminUser.getId(),
                    credentialRecord.getCredentialId().getBytes(),
                    credentialRecord.getPublicKey().getBytes(),
                    bytesOrNull(credentialRecord.getAttestationObject()),
                    bytesOrNull(credentialRecord.getAttestationClientDataJSON()),
                    credentialRecord.getSignatureCount(),
                    null,
                    "none");
            return;
        }

        long storedSignatureCounter = adminUser.getSignatureCounter();
        long reportedSignatureCounter = credentialRecord.getSignatureCount();
        if (reportedSignatureCounter == 0 && storedSignatureCounter == 0) {
            return;
        }
        webAuthnCredentialStore.verifyAndUpdateSignatureCounter(
                credentialRecord.getCredentialId().getBytes(),
                reportedSignatureCounter,
                credentialRecord.getLastUsed());
    }

    @Override
    @Transactional(readOnly = true)
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return adminUserRepository
                .findByCredentialId(credentialId.getBytes())
                .flatMap(this::toCredentialRecord)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return adminUserRepository
                .findByUserHandle(userId.getBytes())
                .flatMap(this::toCredentialRecord)
                .map(List::of)
                .orElseGet(List::of);
    }

    private PublicKeyCredentialUserEntity toUserEntity(AdminUserEntity adminUser) {
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .name(adminUser.getEmail())
                .displayName(adminUser.getDisplayName())
                .id(new Bytes(adminUser.getUserHandle()))
                .build();
    }

    private Optional<CredentialRecord> toCredentialRecord(AdminUserEntity adminUser) {
        if (adminUser.getCredentialId() == null
                || adminUser.getPublicKeyCose() == null
                || adminUser.getAttestationObject() == null) {
            return Optional.empty();
        }
        Instant createdAt =
                adminUser.getCreatedAt() == null ? clock.instant() : adminUser.getCreatedAt();
        Instant lastUsedAt =
                adminUser.getLastUsedAt() == null ? createdAt : adminUser.getLastUsedAt();
        return Optional.of(
                ImmutableCredentialRecord.builder()
                        .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                        .credentialId(new Bytes(adminUser.getCredentialId()))
                        .userEntityUserId(new Bytes(adminUser.getUserHandle()))
                        .publicKey(new ImmutablePublicKeyCose(adminUser.getPublicKeyCose()))
                        .signatureCount(adminUser.getSignatureCounter())
                        .uvInitialized(true)
                        .transports(Set.of())
                        .backupEligible(false)
                        .backupState(false)
                        .attestationObject(new Bytes(adminUser.getAttestationObject()))
                        .attestationClientDataJSON(
                                bytesObjectOrNull(adminUser.getAttestationClientDataJson()))
                        .created(createdAt)
                        .lastUsed(lastUsedAt)
                        .label(DEFAULT_CREDENTIAL_LABEL)
                        .build());
    }

    private static byte[] bytesOrNull(Bytes bytes) {
        return bytes == null ? null : bytes.getBytes();
    }

    private static Bytes bytesObjectOrNull(byte[] bytes) {
        return bytes == null ? null : new Bytes(bytes);
    }
}
