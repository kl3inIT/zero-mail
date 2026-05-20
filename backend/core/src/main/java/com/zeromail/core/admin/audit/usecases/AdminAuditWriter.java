package com.zeromail.core.admin.audit.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.persistence.AdminAuditEventRepository;
import com.zeromail.core.admin.audit.persistence.lowlevel.AdminAuditEventWriteRepository;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditWriter {

    private static final byte[] EMPTY_HASH = new byte[0];
    private static final UUID SYSTEM_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SYSTEM_ACTOR_EMAIL = "<system>";

    private final AdminAuditEventWriteRepository adminAuditEventWriteRepository;
    private final AdminAuditEventRepository adminAuditEventRepository;
    private final AdminAuditHmacSecretProvider adminAuditHmacSecretProvider;
    private final HmacChainHasher hmacChainHasher;
    private final Clock clock;

    public AdminAuditWriter(
            AdminAuditEventWriteRepository adminAuditEventWriteRepository,
            AdminAuditEventRepository adminAuditEventRepository,
            AdminAuditHmacSecretProvider adminAuditHmacSecretProvider,
            Clock clock) {
        this.adminAuditEventWriteRepository =
                Objects.requireNonNull(
                        adminAuditEventWriteRepository,
                        "adminAuditEventWriteRepository must not be null");
        this.adminAuditEventRepository =
                Objects.requireNonNull(
                        adminAuditEventRepository, "adminAuditEventRepository must not be null");
        this.adminAuditHmacSecretProvider =
                Objects.requireNonNull(
                        adminAuditHmacSecretProvider,
                        "adminAuditHmacSecretProvider must not be null");
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
        adminAuditEventWriteRepository.acquireChainAdvisoryLock();
        byte[] previousHash =
                adminAuditEventRepository.findLatestHmacForUpdate().orElse(EMPTY_HASH);
        long chainIndex = adminAuditEventWriteRepository.reserveNextChainIndex();
        String canonicalBeforeStateJson =
                adminAuditEventWriteRepository.canonicalJson(beforeStateJson);
        String canonicalAfterStateJson =
                adminAuditEventWriteRepository.canonicalJson(afterStateJson);
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
                hmacChainHasher.computeHash(
                        adminAuditHmacSecretProvider.secret(),
                        previousHash,
                        unsignedAuditChainEntry);
        adminAuditEventWriteRepository.insertAuditEvent(
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
        adminAuditEventWriteRepository.insertReadEvent(
                readEventId,
                actor.id(),
                actor.email(),
                requireText(action, "action"),
                targetKind,
                targetId);
        return readEventId;
    }

    private static String requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + " must not be blank");
        }
        return value;
    }
}
