package com.zeromail.api.controllers.billing;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class BillingBalanceMultiTenantLeakTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;

    @Test
    void concurrent_virtual_thread_balance_requests_never_cross_tenant() throws Exception {
        List<Seed> seeds =
                IntStream.range(0, 10)
                        .mapToObj(
                                seedIndex ->
                                        seedTenant(
                                                "billing-leak-" + seedIndex, (seedIndex + 1) * 10))
                        .toList();
        RestClient client = RestClient.create("http://localhost:" + port);

        try (var taskScope = StructuredTaskScope.<BillingBalanceResponse>open()) {
            var balanceTasks =
                    seeds.stream()
                            .map(seed -> taskScope.fork(() -> fetchBalance(client, seed)))
                            .toList();
            taskScope.join();

            for (int seedIndex = 0; seedIndex < seeds.size(); seedIndex++) {
                BillingBalanceResponse observedResponse = balanceTasks.get(seedIndex).get();
                assertThat(observedResponse.availableCredits())
                        .isEqualTo(seeds.get(seedIndex).expectedCredits() + 300);
                assertThat(observedResponse.heldCredits()).isZero();
                assertThat(observedResponse.currency()).isEqualTo("credits");
                assertThat(observedResponse.monthlyCredits()).isEqualTo(300);
                assertThat(observedResponse.additionalCredits())
                        .isEqualTo(seeds.get(seedIndex).expectedCredits());
                assertThat(observedResponse.monthlyCreditAllowance()).isEqualTo(300);
            }
        }
    }

    private Seed seedTenant(String label, int credits) {
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, label));
        String googleSubject = "sub-" + label;
        String email = label + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            userRepository.save(
                                    new UserEntity(
                                            UUID.randomUUID(), tenantId, googleSubject, email));
                            creditLedgerEntryRepository.saveAndFlush(
                                    CreditLedgerEntryEntity.topup(
                                            UUID.randomUUID(),
                                            tenantId,
                                            credits,
                                            "TEST-LEAK-" + tenantId));
                        });
        testSessionMinter.mint(googleSubject, email);
        return new Seed(tenantId, googleSubject, email, credits);
    }

    private BillingBalanceResponse fetchBalance(RestClient client, Seed seed) {
        return client.get()
                .uri("/api/credits/balance")
                .header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                .header(TestSessionSupport.HEADER_EMAIL, seed.email())
                .retrieve()
                .body(BillingBalanceResponse.class);
    }

    private record Seed(UUID tenantId, String googleSubject, String email, int expectedCredits) {}
}
