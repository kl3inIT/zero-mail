package com.zeromail.core.admin.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.support.PostgresContainerTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminUserPersistenceTest extends PostgresContainerTest {

    @Autowired private AdminUserRepository adminUserRepository;

    @Test
    void pending_admin_can_be_marked_active_with_webauthn_credential() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000831");
        AdminUserEntity pendingAdminUser =
                new AdminUserEntity(
                        adminUserId,
                        "pending-admin@example.com",
                        "Pending Admin",
                        new byte[] {0x21},
                        AdminStatus.PENDING_ENROLLMENT);
        adminUserRepository.save(pendingAdminUser);

        adminUserRepository.markActive(
                adminUserId,
                new byte[] {0x31},
                new byte[] {0x41},
                7L,
                UUID.fromString("00000000-0000-4000-8000-000000000832"),
                "none");

        AdminUserEntity activeAdminUser = adminUserRepository.findById(adminUserId).orElseThrow();
        assertThat(activeAdminUser.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(activeAdminUser.getCredentialId()).containsExactly(0x31);
        assertThat(activeAdminUser.getPublicKeyCose()).containsExactly(0x41);
        assertThat(activeAdminUser.getSignatureCounter()).isEqualTo(7L);
    }

    @Test
    void signature_counter_increment_is_monotonic() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000833");
        AdminUserEntity activeAdminUser =
                new AdminUserEntity(
                        adminUserId,
                        "counter-admin@example.com",
                        "Counter Admin",
                        new byte[] {0x22},
                        AdminStatus.ACTIVE);
        activeAdminUser.activate(
                new byte[] {0x32},
                new byte[] {0x42},
                10L,
                UUID.fromString("00000000-0000-4000-8000-000000000834"),
                "none");
        adminUserRepository.save(activeAdminUser);

        int updatedRows =
                adminUserRepository.incrementSignCounter(
                        adminUserId, 11L, Instant.parse("2026-05-19T18:36:17Z"));
        Optional<AdminUserEntity> reloadedAdminUser =
                adminUserRepository.findByCredentialId(new byte[] {0x32});

        assertThat(updatedRows).isEqualTo(1);
        assertThat(reloadedAdminUser).isPresent();
        assertThat(reloadedAdminUser.orElseThrow().getSignatureCounter()).isEqualTo(11L);
    }
}
