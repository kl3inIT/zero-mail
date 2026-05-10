package com.zeromail.api;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import com.zeromail.api.controllers.account.AccountDeletionController;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.onboarding.persistence.OnboardingSelectionEntity;
import com.zeromail.core.onboarding.persistence.OnboardingSelectionRepository;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class AccountDeletionE2ETest extends ApiPostgresTestBase {

    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired GmailConnectionRepository conns;
    @Autowired OnboardingSelectionRepository onboarding;
    @Autowired AccountDeletionController deletion;

    @Test
    void delete_cascades_all_tenant_rows() {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, "t"));

        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
            users.save(new UserEntity(UUID.randomUUID(), tenantId, "gs-1", "a@example.com"));
            onboarding.save(new OnboardingSelectionEntity(UUID.randomUUID(), tenantId, "archive-receipts"));
            var gc = new GmailConnectionEntity(
                    UUID.randomUUID(), tenantId, "a@example.com", GmailConnectionStatus.CONNECTED);
            gc.setConnectedAt(Instant.now());
            gc.setRefreshTokenEncrypted(new byte[]{1, 2, 3});
            conns.save(gc);

            deletion.deleteAccount();

            assertThat(onboarding.findByTenantId(tenantId)).isEmpty();
            assertThat(conns.findByTenantId(tenantId)).isEmpty();
            assertThat(users.findFirstByTenantId(tenantId)).isEmpty();
        });
        assertThat(tenants.findById(tenantId)).isEmpty();
    }
}
