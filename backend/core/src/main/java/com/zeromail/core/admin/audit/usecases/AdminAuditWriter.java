package com.zeromail.core.admin.audit.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.persistence.AdminAuditEventRepository;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.config.ZeroMailCoreProperties;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditWriter {

    private static final long AUDIT_CHAIN_ADVISORY_LOCK_ID = 8_001_001L;
    private static final byte[] EMPTY_HASH = new byte[0];
    private static final UUID SYSTEM_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SYSTEM_ACTOR_EMAIL = "<system>";

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditEventRepository adminAuditEventRepository;
    private final HmacChainHasher hmacChainHasher;
    private final ZeroMailCoreProperties coreProperties;
    private final Clock clock;

    public AdminAuditWriter(
            JdbcTemplate jdbcTemplate,
            AdminAuditEventRepository adminAuditEventRepository,
            ZeroMailCoreProperties coreProperties,
            Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.adminAuditEventRepository =
                Objects.requireNonNull(
                        adminAuditEventRepository, "adminAuditEventRepository must not be null");
        this.coreProperties =
                Objects.requireNonNull(coreProperties, "coreProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        hmacChainHasher = new HmacChainHasher();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(
            AdminAuditAction action,
            String targetKind,
            UUID targetId,
            String beforeStateJson,
            String afterStateJson,
            String reason,
            String requestIp,
            UUID requestId) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        return appendForActor(
                adminUser.id(),
                adminUser.email(),
                action,
                targetKind,
                targetId,
                beforeStateJson,
                afterStateJson,
                reason,
                requestIp,
                requestId);
    }

    @Transactional
    public UUID appendAsSystem(
            AdminAuditAction action,
            String targetKind,
            UUID targetId,
            String beforeStateJson,
            String afterStateJson,
            String reason,
            String requestIp,
            UUID requestId) {
        return appendForActor(
                SYSTEM_ACTOR_ID,
                SYSTEM_ACTOR_EMAIL,
                action,
                targetKind,
                targetId,
                beforeStateJson,
                afterStateJson,
                reason,
                requestIp,
                requestId);
    }

    private UUID appendForActor(
            UUID actorUserId,
            String actorEmail,
            AdminAuditAction action,
            String targetKind,
            UUID targetId,
            String beforeStateJson,
            String afterStateJson,
            String reason,
            String requestIp,
            UUID requestId) {
        AdminAuditAction auditAction = Objects.requireNonNull(action, "action must not be null");
        UUID auditId = UUID.randomUUID();
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?)", _ -> {}, AUDIT_CHAIN_ADVISORY_LOCK_ID);
        byte[] previousHash =
                adminAuditEventRepository.findLatestHmacForUpdate().orElse(EMPTY_HASH);
        Long chainIndex =
                jdbcTemplate.queryForObject(
                        "SELECT nextval('admin_audit_event_chain_index_seq')", Long.class);
        if (chainIndex == null) {
            throw new IllegalStateException("Unable to reserve admin audit chain index");
        }
        String canonicalBeforeStateJson = canonicalJson(beforeStateJson);
        String canonicalAfterStateJson = canonicalJson(afterStateJson);
        long canonicalTimestampMs = clock.instant().toEpochMilli();
        HmacChainHasher.AuditChainEntry unsignedAuditChainEntry =
                new HmacChainHasher.AuditChainEntry(
                        chainIndex,
                        actorUserId,
                        actorEmail,
                        auditAction.id(),
                        targetKind,
                        targetId,
                        canonicalBeforeStateJson,
                        canonicalAfterStateJson,
                        reason,
                        requestIp,
                        requestId,
                        canonicalTimestampMs,
                        EMPTY_HASH);
        byte[] hmacChainHash =
                hmacChainHasher.computeHash(hmacSecret(), previousHash, unsignedAuditChainEntry);
        jdbcTemplate.update(
                """
                INSERT INTO admin_audit_event(
                    id, chain_index, actor_user_id, actor_email, action, target_kind, target_id,
                    before_state_json, after_state_json, reason, request_ip, request_id,
                    canonical_timestamp_ms, hmac_chain_hash
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, CAST(? AS inet), ?, ?, ?)
                """,
                auditId,
                chainIndex,
                actorUserId,
                actorEmail,
                auditAction.id(),
                targetKind,
                targetId,
                canonicalBeforeStateJson,
                canonicalAfterStateJson,
                reason,
                requestIp,
                requestId,
                canonicalTimestampMs,
                hmacChainHash);
        return auditId;
    }

    @Transactional
    public UUID writeReadEvent(
            AdminUser adminUser, String action, String targetKind, UUID targetId) {
        AdminUser actor = Objects.requireNonNull(adminUser, "adminUser must not be null");
        UUID readEventId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO admin_read_event(
                    id, actor_user_id, actor_email, action, target_kind, target_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                readEventId,
                actor.id(),
                actor.email(),
                requireText(action, "action"),
                targetKind,
                targetId);
        return readEventId;
    }

    private String canonicalJson(String jsonValue) {
        if (jsonValue == null) {
            return null;
        }
        return jdbcTemplate.queryForObject(
                "SELECT CAST(? AS jsonb)::text", String.class, jsonValue);
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

    private static String requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + " must not be blank");
        }
        return value;
    }
}
