package com.zeromail.core.admin.auth.usecases;

import com.zeromail.core.admin.auth.exception.AdminAuthException;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebAuthnCredentialStore {

    private final AdminUserRepository adminUserRepository;

    public WebAuthnCredentialStore(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Transactional
    public void saveCredential(
            UUID adminUserId,
            byte[] credentialId,
            byte[] publicKeyCose,
            long signatureCounter,
            UUID aaguid,
            String attestationFormat) {
        int updatedRows =
                adminUserRepository.markActive(
                        adminUserId,
                        copy(credentialId),
                        copy(publicKeyCose),
                        signatureCounter,
                        aaguid,
                        attestationFormat);
        if (updatedRows != 1) {
            throw new AdminAuthException("Unable to activate admin WebAuthn credential");
        }
    }

    @Transactional(readOnly = true)
    public Optional<AdminCredential> findByCredentialId(byte[] credentialId) {
        return adminUserRepository.findByCredentialId(credentialId).map(AdminCredential::from);
    }

    @Transactional
    public void verifyAndUpdateSignatureCounter(
            byte[] credentialId, long reportedSignatureCounter, Instant lastUsedAt) {
        AdminUserEntity adminUser =
                adminUserRepository
                        .findByCredentialId(credentialId)
                        .orElseThrow(() -> new AdminAuthException("Admin credential not found"));
        if (reportedSignatureCounter <= adminUser.getSignatureCounter()) {
            throw new AdminAuthException("WebAuthn replay suspected for admin credential");
        }
        int updatedRows =
                adminUserRepository.incrementSignCounter(
                        adminUser.getId(), reportedSignatureCounter, lastUsedAt);
        if (updatedRows != 1) {
            throw new AdminAuthException("Unable to update admin credential counter");
        }
    }

    private static byte[] copy(byte[] bytes) {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    public record AdminCredential(
            UUID adminUserId,
            byte[] credentialId,
            byte[] publicKeyCose,
            long signatureCounter,
            UUID aaguid,
            String attestationFormat) {

        public AdminCredential {
            credentialId = copy(credentialId);
            publicKeyCose = copy(publicKeyCose);
        }

        private static AdminCredential from(AdminUserEntity adminUser) {
            return new AdminCredential(
                    adminUser.getId(),
                    adminUser.getCredentialId(),
                    adminUser.getPublicKeyCose(),
                    adminUser.getSignatureCounter(),
                    adminUser.getAaguid(),
                    adminUser.getAttestationFormat());
        }

        @Override
        public byte[] credentialId() {
            return copy(credentialId);
        }

        @Override
        public byte[] publicKeyCose() {
            return copy(publicKeyCose);
        }
    }
}
