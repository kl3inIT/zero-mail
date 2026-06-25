package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

class AdminWebAuthnOptionsUsernameFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticate_options_binds_active_admin_email_for_downstream_webauthn_filter()
            throws Exception {
        AdminUserRepository adminUserRepository = mock(AdminUserRepository.class);
        AdminUserEntity adminUser =
                new AdminUserEntity(
                        UUID.fromString("db3e878a-c1bd-4afe-adc5-eec804211ad1"),
                        "nhudinhnhat2004@gmail.com",
                        "nhudinhnhat2004@gmail.com",
                        new byte[32],
                        AdminStatus.ACTIVE);
        adminUser.activate(
                new byte[20], new byte[77], new byte[182], new byte[137], 0, null, "none");
        when(adminUserRepository.findByEmailIgnoreCase("nhudinhnhat2004@gmail.com"))
                .thenReturn(Optional.of(adminUser));
        AdminWebAuthnOptionsUsernameFilter filter =
                new AdminWebAuthnOptionsUsernameFilter(adminUserRepository, new ObjectMapper());
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/webauthn/authenticate/options");
        request.setContentType("application/json");
        request.setContent("{\"email\":\"nhudinhnhat2004@gmail.com\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain =
                (_, _) -> {
                    Authentication authentication =
                            SecurityContextHolder.getContext().getAuthentication();
                    assertThat(authentication).isNotNull();
                    assertThat(authentication.isAuthenticated()).isTrue();
                    assertThat(authentication.getName()).isEqualTo("nhudinhnhat2004@gmail.com");
                    assertThat(authentication.getAuthorities())
                            .extracting("authority")
                            .containsExactly("ROLE_ADMIN");
                };

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
