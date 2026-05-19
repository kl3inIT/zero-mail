package com.zeromail.core.admin.audit.usecases;

import com.zeromail.core.config.ZeroMailCoreProperties;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditChainVerifier {

    private final JdbcTemplate jdbcTemplate;
    private final ZeroMailCoreProperties coreProperties;
    private final HmacChainHasher hmacChainHasher;

    public AdminAuditChainVerifier(
            JdbcTemplate jdbcTemplate, ZeroMailCoreProperties coreProperties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.coreProperties =
                Objects.requireNonNull(coreProperties, "coreProperties must not be null");
        hmacChainHasher = new HmacChainHasher();
    }

    @Transactional(readOnly = true)
    public OptionalLong verifyOnce() {
        List<HmacChainHasher.AuditChainEntry> auditChainEntries =
                jdbcTemplate.query(
                        """
                        SELECT chain_index, actor_user_id, actor_email, action, target_kind,
                               target_id, before_state_json::text AS before_state_json_text,
                               after_state_json::text AS after_state_json_text, reason,
                               host(request_ip) AS request_ip_text, request_id,
                               canonical_timestamp_ms, hmac_chain_hash
                        FROM admin_audit_event
                        ORDER BY chain_index ASC
                        """,
                        (resultSet, _) ->
                                new HmacChainHasher.AuditChainEntry(
                                        resultSet.getLong("chain_index"),
                                        resultSet.getObject("actor_user_id", UUID.class),
                                        resultSet.getString("actor_email"),
                                        resultSet.getString("action"),
                                        resultSet.getString("target_kind"),
                                        resultSet.getObject("target_id", UUID.class),
                                        resultSet.getString("before_state_json_text"),
                                        resultSet.getString("after_state_json_text"),
                                        resultSet.getString("reason"),
                                        resultSet.getString("request_ip_text"),
                                        resultSet.getObject("request_id", UUID.class),
                                        resultSet.getLong("canonical_timestamp_ms"),
                                        resultSet.getBytes("hmac_chain_hash")));
        return hmacChainHasher.findFirstMismatch(hmacSecret(), auditChainEntries);
    }

    private byte[] hmacSecret() {
        String hmacSecretBase64 = coreProperties.admin().audit().hmacKekBase64();
        if (hmacSecretBase64 == null || hmacSecretBase64.isBlank()) {
            throw new IllegalStateException(
                    "zero-mail.admin.audit.hmac-kek-base64 must be configured");
        }
        byte[] hmacSecret = Base64.getDecoder().decode(hmacSecretBase64);
        if (hmacSecret.length < 32) {
            throw new IllegalStateException(
                    "zero-mail.admin.audit.hmac-kek-base64 must decode to at least 32 bytes");
        }
        return hmacSecret;
    }
}
