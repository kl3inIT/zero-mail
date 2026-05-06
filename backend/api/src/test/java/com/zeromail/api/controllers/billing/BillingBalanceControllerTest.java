package com.zeromail.api.controllers.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.zeromail.api.dto.billing.BillingBalanceResponse;
import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class BillingBalanceControllerTest extends ApiPostgresTestBase {

  @LocalServerPort int port;
  @Autowired TenantRepository tenantRepository;
  @Autowired UserRepository userRepository;
  @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
  @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;

  @Test
  void authenticated_balance_returns_shape() {
    Seed seed = seedUser("billing-balance");
    seedTopup(seed.tenantId(), 42);

    BillingBalanceResponse response =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/api/billing/balance")
            .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
            .header(TestSessionSupport.HEADER_EMAIL, seed.email())
            .retrieve()
            .body(BillingBalanceResponse.class);

    assertThat(response.availableCredits()).isEqualTo(42);
    assertThat(response.heldCredits()).isZero();
    assertThat(response.currency()).isEqualTo("credits");
  }

  private Seed seedUser(String label) {
    UUID tenantId = UUID.randomUUID();
    tenantRepository.save(new TenantEntity(tenantId, label));
    String googleSubject = "sub-" + label;
    String email = label + "@example.test";
    ScopedValue.where(TenantContext.TENANT, tenantId.toString())
        .run(
            () ->
                userRepository.save(
                    new UserEntity(UUID.randomUUID(), tenantId, googleSubject, email)));
    testSessionMinter.mint(googleSubject, email);
    return new Seed(tenantId, googleSubject, email);
  }

  private void seedTopup(UUID tenantId, int credits) {
    ScopedValue.where(TenantContext.TENANT, tenantId.toString())
        .run(
            () ->
                creditLedgerEntryRepository.saveAndFlush(
                    CreditLedgerEntryEntity.topup(
                        UUID.randomUUID(), tenantId, credits, "SEPAY-BALANCE-" + tenantId)));
  }

  private record Seed(UUID tenantId, String googleSubject, String email) {}
}
