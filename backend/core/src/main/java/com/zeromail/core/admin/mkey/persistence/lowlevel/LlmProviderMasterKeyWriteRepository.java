package com.zeromail.core.admin.mkey.persistence.lowlevel;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.MasterKeyStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC writes against {@code llm_provider_master_key}. Phase B v2 reshapes this around the
 * (provider, key_id) composite identity: writes target a specific row, never "the row" for a
 * provider.
 *
 * <p>The legacy {@code setFeatureDefault} method was removed — its UPDATE targeted the {@code
 * feature_default_provider_chat/triage/draft} columns that changelog 069 dropped, so it would have
 * crashed on first call. The v2 admin surface drives feature defaults through {@code
 * feature_default_provider} directly (see the tier matrix endpoints).
 */
@Repository
public class LlmProviderMasterKeyWriteRepository {

    private final JdbcTemplate jdbcTemplate;

    public LlmProviderMasterKeyWriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Inserts a new row for a provider at the requested priority. Caller is responsible for
     * shifting existing rows when reordering — this method only INSERTs.
     */
    public void insertKey(
            LlmProvider provider,
            UUID keyId,
            int priority,
            MasterKeyStatus status,
            String label,
            KeyFormat keyFormat,
            byte[] encryptedKey,
            short kekVersion,
            UUID createdByUserId,
            Instant createdAt,
            String baseUrl,
            String maskedKey) {
        jdbcTemplate.update(
                """
                INSERT INTO llm_provider_master_key
                  (provider, key_id, priority, status, label, key_format, encrypted_key,
                   kek_version, provider_secret_version, created_by_user_id, created_at,
                   last_rotated_at, base_url, masked_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)
                """,
                provider.id(),
                keyId,
                priority,
                status.id(),
                label,
                keyFormat == null ? null : keyFormat.id(),
                encryptedKey,
                (short) kekVersion,
                createdByUserId,
                createdAt,
                encryptedKey == null ? null : createdAt,
                baseUrl,
                maskedKey);
    }

    /**
     * Replaces the encrypted material on an existing row and bumps {@code provider_secret_version}.
     * Returns the new secret version. Throws when the row does not exist.
     */
    public long replaceKeyAndReturnVersion(
            LlmProvider provider,
            UUID keyId,
            KeyFormat keyFormat,
            byte[] encryptedKey,
            short kekVersion,
            UUID actorId,
            Instant lastRotatedAt,
            String baseUrl,
            String maskedKey) {
        Long updatedVersion =
                jdbcTemplate.queryForObject(
                        """
                        UPDATE llm_provider_master_key
                        SET key_format = ?,
                            encrypted_key = ?,
                            kek_version = ?,
                            provider_secret_version = provider_secret_version + 1,
                            status = CASE WHEN status = 'REVOKED' THEN 'ACTIVE' ELSE status END,
                            created_by_user_id = COALESCE(created_by_user_id, ?),
                            last_rotated_at = ?,
                            base_url = ?,
                            masked_key = ?
                        WHERE provider = ? AND key_id = ?
                        RETURNING provider_secret_version
                        """,
                        Long.class,
                        keyFormat.id(),
                        encryptedKey,
                        kekVersion,
                        actorId,
                        lastRotatedAt,
                        baseUrl,
                        maskedKey,
                        provider.id(),
                        keyId);
        if (updatedVersion == null) {
            throw new EmptyResultDataAccessException(
                    "No llm_provider_master_key row for provider="
                            + provider.id()
                            + " keyId="
                            + keyId,
                    1);
        }
        return updatedVersion;
    }

    /** Marks an existing key as {@code REVOKED}. Idempotent. */
    public int revokeKey(LlmProvider provider, UUID keyId) {
        return jdbcTemplate.update(
                "UPDATE llm_provider_master_key SET status = 'REVOKED' WHERE provider = ? AND key_id = ?",
                provider.id(),
                keyId);
    }

    /** Activates a {@code PENDING}/{@code REVOKED} key once verification passes. */
    public int markActive(LlmProvider provider, UUID keyId) {
        return jdbcTemplate.update(
                "UPDATE llm_provider_master_key SET status = 'ACTIVE' WHERE provider = ? AND key_id = ?",
                provider.id(),
                keyId);
    }

    /**
     * Atomically reorders priorities within a provider. Uses a temporary negative priority trick to
     * avoid colliding with the {@code uq_*_priority} deferrable unique constraint mid-update.
     */
    public void setPriority(LlmProvider provider, UUID keyId, int newPriority) {
        jdbcTemplate.update(
                "UPDATE llm_provider_master_key SET priority = ? WHERE provider = ? AND key_id = ?",
                newPriority,
                provider.id(),
                keyId);
    }

    /** Updates the operator-facing label. */
    public int updateLabel(LlmProvider provider, UUID keyId, String label) {
        return jdbcTemplate.update(
                "UPDATE llm_provider_master_key SET label = ? WHERE provider = ? AND key_id = ?",
                label,
                provider.id(),
                keyId);
    }

    /** Updates label + baseUrl in a single statement. Either field may be null. */
    public int updateLabelAndBaseUrl(
            LlmProvider provider, UUID keyId, String label, String baseUrl) {
        return jdbcTemplate.update(
                "UPDATE llm_provider_master_key SET label = ?, base_url = ? WHERE provider = ? AND key_id = ?",
                label,
                baseUrl,
                provider.id(),
                keyId);
    }

    /** Hard-deletes a key row. Use sparingly — usually prefer {@link #revokeKey}. */
    public int deleteKey(LlmProvider provider, UUID keyId) {
        return jdbcTemplate.update(
                "DELETE FROM llm_provider_master_key WHERE provider = ? AND key_id = ?",
                provider.id(),
                keyId);
    }
}
