package com.zeromail.api.admin;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import com.zeromail.core.admin.auth.usecases.EnrollmentTokenService;
import com.zeromail.core.config.ZeroMailCoreProperties;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final int USER_HANDLE_BYTES = 32;
    private static final String ADMIN_ENROLLMENT_BASE_URL =
            "https://admin.zeromail.com/enroll?token=";

    private final ZeroMailCoreProperties zeroMailCoreProperties;
    private final AdminUserRepository adminUserRepository;
    private final EnrollmentTokenService enrollmentTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminBootstrapRunner(
            ZeroMailCoreProperties zeroMailCoreProperties,
            AdminUserRepository adminUserRepository,
            EnrollmentTokenService enrollmentTokenService) {
        this.zeroMailCoreProperties = zeroMailCoreProperties;
        this.adminUserRepository = adminUserRepository;
        this.enrollmentTokenService = enrollmentTokenService;
    }

    @Override
    public void run(String... args) {
        for (String bootstrapEmail : zeroMailCoreProperties.admin().bootstrapEmails()) {
            String normalizedEmail = bootstrapEmail.trim().toLowerCase(Locale.ROOT);
            AdminUserEntity adminUser =
                    adminUserRepository
                            .findByEmailIgnoreCase(normalizedEmail)
                            .orElseGet(() -> createPendingAdmin(normalizedEmail));
            if (adminUser.getStatus() != AdminStatus.PENDING_ENROLLMENT) {
                continue;
            }
            EnrollmentTokenService.IssuedEnrollmentToken issuedEnrollmentToken =
                    enrollmentTokenService.mintOrReuseToken(adminUser.getId(), normalizedEmail);
            // Direct STDOUT is intentional; see docs/ops/admin-interface-freeze.md §Enrollment
            // Routing.
            System.out.println(
                    "[ZeroMail Admin Bootstrap] "
                            + normalizedEmail
                            + " enrollment URL: "
                            + ADMIN_ENROLLMENT_BASE_URL
                            + issuedEnrollmentToken.token()
                            + " (valid 10 minutes)");
        }
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
}
