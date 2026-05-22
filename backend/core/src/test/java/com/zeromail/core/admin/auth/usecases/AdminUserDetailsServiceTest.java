package com.zeromail.core.admin.auth.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AdminUserDetailsServiceTest extends PostgresContainerTest {

    @Autowired private AdminUserRepository adminUserRepository;

    @Autowired private AdminUserDetailsService adminUserDetailsService;

    @Test
    void active_admin_loads_with_role_admin() {
        adminUserRepository.save(
                new AdminUserEntity(
                        UUID.fromString("00000000-0000-4000-8000-000000000841"),
                        "active-details@example.com",
                        "Active Details",
                        new byte[] {0x51},
                        AdminStatus.ACTIVE));

        var userDetails = adminUserDetailsService.loadUserByUsername("active-details@example.com");

        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    void pending_admin_must_complete_enrollment_first() {
        adminUserRepository.save(
                new AdminUserEntity(
                        UUID.fromString("00000000-0000-4000-8000-000000000842"),
                        "pending-details@example.com",
                        "Pending Details",
                        new byte[] {0x52},
                        AdminStatus.PENDING_ENROLLMENT));

        assertThatThrownBy(
                        () ->
                                adminUserDetailsService.loadUserByUsername(
                                        "pending-details@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("complete enrollment first");
    }
}
