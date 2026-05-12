package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
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
class MultiTenantLeakIntegrationTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired TestSessionSupport.TestSessionMinter minter;

    record Seed(UUID tenantId, String googleSub, String email) {}

    @Test
    void concurrent_virtual_thread_requests_never_cross_tenant() throws Exception {
        int N = 100;
        List<Seed> seeds = IntStream.range(0, N).mapToObj(i -> seedTenant("t-" + i)).toList();
        RestClient client = RestClient.create("http://localhost:" + port);

        try (var scope = StructuredTaskScope.<String>open()) {
            var subtasks =
                    seeds.stream().map(s -> scope.fork(() -> fetchTenantEcho(client, s))).toList();
            scope.join();
            for (int i = 0; i < N; i++) {
                String observed = subtasks.get(i).get();
                assertThat(observed).isEqualTo(seeds.get(i).tenantId().toString());
            }
        }
    }

    private Seed seedTenant(String label) {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, label));
        var user =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        users.save(
                                                new UserEntity(
                                                        UUID.randomUUID(),
                                                        tenantId,
                                                        "sub-" + label,
                                                        label + "@example.com")));
        // Register the seed so TestSessionSupport's auth filter picks up the headers below.
        minter.mint(user.getGoogleSubject(), user.getEmail());
        return new Seed(tenantId, user.getGoogleSubject(), user.getEmail());
    }

    private String fetchTenantEcho(RestClient client, Seed s) {
        return client.get()
                .uri("/debug/tenant-echo")
                .header(TestSessionSupport.HEADER_SUBJECT, s.googleSub())
                .header(TestSessionSupport.HEADER_EMAIL, s.email())
                .retrieve()
                .body(String.class);
    }
}
