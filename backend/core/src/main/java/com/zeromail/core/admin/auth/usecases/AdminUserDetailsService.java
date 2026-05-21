package com.zeromail.core.admin.auth.usecases;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    public AdminUserDetailsService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUserEntity adminUser =
                adminUserRepository
                        .findByEmailIgnoreCase(username)
                        .orElseThrow(
                                () ->
                                        new UsernameNotFoundException(
                                                "Admin user not found: " + username));
        if (adminUser.getStatus() == AdminStatus.PENDING_ENROLLMENT) {
            throw new UsernameNotFoundException("Admin user must complete enrollment first");
        }

        return User.withUsername(adminUser.getEmail())
                .password("{noop}webauthn")
                .authorities("ROLE_ADMIN")
                .disabled(adminUser.getStatus() == AdminStatus.REVOKED)
                .build();
    }
}
