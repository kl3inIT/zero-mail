package com.zeromail.core.admin.auth.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, UUID> {

    Optional<AdminUserEntity> findByEmailIgnoreCase(String email);

    Optional<AdminUserEntity> findByCredentialId(byte[] credentialId);

    Optional<AdminUserEntity> findByUserHandle(byte[] userHandle);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            value =
                    """
                    UPDATE admin_users
                    SET status = 'ACTIVE',
                        credential_id = :credentialId,
                        public_key_cose = :publicKeyCose,
                        signature_counter = :signatureCounter,
                        aaguid = :aaguid,
                        attestation_format = :attestationFormat
                    WHERE id = :adminUserId
                    """,
            nativeQuery = true)
    int markActive(
            @Param("adminUserId") UUID adminUserId,
            @Param("credentialId") byte[] credentialId,
            @Param("publicKeyCose") byte[] publicKeyCose,
            @Param("signatureCounter") long signatureCounter,
            @Param("aaguid") UUID aaguid,
            @Param("attestationFormat") String attestationFormat);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            value =
                    """
                    UPDATE admin_users
                    SET signature_counter = :newCounter,
                        last_used_at = :lastUsedAt
                    WHERE id = :adminUserId
                      AND signature_counter < :newCounter
                    """,
            nativeQuery = true)
    int incrementSignCounter(
            @Param("adminUserId") UUID adminUserId,
            @Param("newCounter") long newCounter,
            @Param("lastUsedAt") Instant lastUsedAt);
}
