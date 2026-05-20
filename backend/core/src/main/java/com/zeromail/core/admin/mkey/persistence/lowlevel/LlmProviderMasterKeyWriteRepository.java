package com.zeromail.core.admin.mkey.persistence.lowlevel;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.MasterKeyFeature;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC writes against {@code llm_provider_master_key}. Centralises the per-column UPDATE shape so
 * {@code MasterKeyAdminService} stays free of inline SQL.
 */
@Repository
public class LlmProviderMasterKeyWriteRepository {

    private final JdbcTemplate jdbcTemplate;

    public LlmProviderMasterKeyWriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Clears the feature-default flag on every row then sets it on the given provider row. Two
     * UPDATEs in one transaction so the FE radio-select semantics are atomic.
     */
    public void setFeatureDefault(MasterKeyFeature feature, LlmProvider provider) {
        String columnName = feature.columnName();
        jdbcTemplate.update("UPDATE llm_provider_master_key SET " + columnName + " = FALSE");
        jdbcTemplate.update(
                "UPDATE llm_provider_master_key SET " + columnName + " = TRUE WHERE provider = ?",
                provider.id());
    }

    /**
     * Atomic UPDATE...RETURNING that stores the new encrypted key and bumps {@code
     * provider_secret_version}. Returns {@code null} when no row exists for the provider.
     */
    public Long storeEncryptedKeyAndReturnVersion(
            LlmProvider provider,
            KeyFormat keyFormat,
            byte[] encryptedKey,
            short kekVersion,
            UUID actorId,
            Instant lastRotatedAt,
            String baseUrl) {
        return jdbcTemplate.queryForObject(
                """
                UPDATE llm_provider_master_key
                SET key_format = ?,
                    encrypted_key = ?,
                    kek_version = ?,
                    provider_secret_version = provider_secret_version + 1,
                    created_by_user_id = COALESCE(created_by_user_id, ?),
                    last_rotated_at = ?,
                    base_url = ?
                WHERE provider = ?
                RETURNING provider_secret_version
                """,
                Long.class,
                keyFormat.id(),
                encryptedKey,
                kekVersion,
                actorId,
                lastRotatedAt,
                baseUrl,
                provider.id());
    }
}
