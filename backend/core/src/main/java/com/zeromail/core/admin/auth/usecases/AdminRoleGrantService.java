package com.zeromail.core.admin.auth.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.exception.AdminAuthException;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRoleGrantService {

    private static final int USER_HANDLE_BYTES = 32;
    private static final String ADMIN_ENROLLMENT_BASE_URL =
            "https://admin.zeromail.com/enroll?token=";
    private static final Pattern ADMIN_EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]{1,200}@[A-Za-z0-9.-]{1,119}\\.[A-Za-z]{2,20}");

    private final AdminUserRepository adminUserRepository;
    private final EnrollmentTokenService enrollmentTokenService;
    private final AdminAuditWriter adminAuditWriter;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public AdminRoleGrantService(
            AdminUserRepository adminUserRepository,
            EnrollmentTokenService enrollmentTokenService,
            AdminAuditWriter adminAuditWriter,
            Clock clock) {
        this.adminUserRepository =
                Objects.requireNonNull(adminUserRepository, "adminUserRepository must not be null");
        this.enrollmentTokenService =
                Objects.requireNonNull(
                        enrollmentTokenService, "enrollmentTokenService must not be null");
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        secureRandom = new SecureRandom();
    }

    @Transactional
    public AdminRoleGrantResult grant(String email, String requestIp, UUID requestId) {
        String normalizedEmail = normalizeEmail(email);
        AdminUserEntity adminUser =
                adminUserRepository
                        .findByEmailIgnoreCase(normalizedEmail)
                        .orElseGet(() -> createPendingAdmin(normalizedEmail));
        if (adminUser.getStatus() == AdminStatus.ACTIVE) {
            throw new AdminAuthException("Admin user is already active");
        }
        if (adminUser.getStatus() == AdminStatus.REVOKED) {
            throw new AdminAuthException("Revoked admin user cannot be granted in place");
        }

        EnrollmentTokenService.IssuedEnrollmentToken issuedEnrollmentToken =
                enrollmentTokenService.mintOrReuseToken(adminUser.getId(), normalizedEmail);
        adminAuditWriter.append(
                AdminAuditAction.ADMIN_GRANTED,
                "admin_user",
                adminUser.getId(),
                null,
                "{\"email\":\"" + normalizedEmail + "\",\"status\":\"PENDING_ENROLLMENT\"}",
                "admin role grant",
                requestIp,
                requestId);
        return new AdminRoleGrantResult(
                adminUser.getId(),
                normalizedEmail,
                ADMIN_ENROLLMENT_BASE_URL + issuedEnrollmentToken.token(),
                issuedEnrollmentToken.expiresAt());
    }

    @Transactional
    public void revoke(UUID adminUserId, String reason, String requestIp, UUID requestId) {
        AdminUserEntity adminUser =
                adminUserRepository
                        .findById(
                                Objects.requireNonNull(adminUserId, "adminUserId must not be null"))
                        .orElseThrow(() -> new AdminAuthException("Admin user not found"));
        if (adminUser.getStatus() == AdminStatus.REVOKED) {
            return;
        }
        Instant revokedAt = clock.instant();
        int updatedRows =
                adminUserRepository.revoke(adminUser.getId(), revokedAt, requireReason(reason));
        if (updatedRows != 1) {
            throw new AdminAuthException("Unable to revoke admin user");
        }
        adminAuditWriter.append(
                AdminAuditAction.ADMIN_REVOKED,
                "admin_user",
                adminUser.getId(),
                "{\"email\":\""
                        + adminUser.getEmail()
                        + "\",\"status\":\""
                        + adminUser.getStatus().id()
                        + "\"}",
                "{\"email\":\"" + adminUser.getEmail() + "\",\"status\":\"REVOKED\"}",
                reason,
                requestIp,
                requestId);
    }

    private AdminUserEntity createPendingAdmin(String normalizedEmail) {
        return adminUserRepository.save(
                new AdminUserEntity(
                        UUID.randomUUID(),
                        normalizedEmail,
                        normalizedEmail,
                        randomUserHandle(),
                        AdminStatus.PENDING_ENROLLMENT));
    }

    private byte[] randomUserHandle() {
        byte[] userHandle = new byte[USER_HANDLE_BYTES];
        secureRandom.nextBytes(userHandle);
        return userHandle;
    }

    private static String normalizeEmail(String email) {
        String normalizedEmail = requireText(email, "email").toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > 320
                || !ADMIN_EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new AdminAuthException("Admin email is invalid");
        }
        return normalizedEmail;
    }

    private static String requireReason(String reason) {
        String normalizedReason = requireText(reason, "reason");
        if (normalizedReason.length() < 8 || normalizedReason.length() > 500) {
            throw new AdminAuthException("Admin revoke reason length is invalid");
        }
        return normalizedReason;
    }

    private static String requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new AdminAuthException(parameterName + " must not be blank");
        }
        return value.trim();
    }

    public record AdminRoleGrantResult(
            UUID adminUserId, String email, String enrollmentUrl, Instant expiresAt) {}
}
