package com.zeromail.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.account.service.OAuthProvisioningService;
import com.zeromail.core.tenant.persistence.TenantRepository;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthProvisioningIntegrationTest extends ApiPostgresTestBase {

    @Autowired OAuthProvisioningService provisioning;
    @Autowired UserRepository users;
    @Autowired TenantRepository tenants;

    @Test
    void first_google_login_creates_tenant_and_user() {
        var user = provisioning.findOrCreateGoogleUser(
                "google-subject-provisioning-test",
                "oauth-provisioning@example.com");

        assertThat(user.getTenantId()).isNotNull();
        assertThat(tenants.findById(user.getTenantId())).isPresent();
        assertThat(users.findByGoogleSubject("google-subject-provisioning-test"))
                .hasValueSatisfying(saved -> assertThat(saved.getTenantId()).isEqualTo(user.getTenantId()));

        var sameUser = provisioning.findOrCreateGoogleUser(
                "google-subject-provisioning-test",
                "oauth-provisioning@example.com");

        assertThat(sameUser.getId()).isEqualTo(user.getId());
        assertThat(sameUser.getTenantId()).isEqualTo(user.getTenantId());
    }
}
