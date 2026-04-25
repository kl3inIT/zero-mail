package com.zeromail.api.security;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.persistence.TenantEntity;
import com.zeromail.core.persistence.TenantRepository;
import com.zeromail.core.persistence.UserEntity;
import com.zeromail.core.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class SessionCookieE2ETest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired TestSessionSupport.TestSessionMinter minter;

    @Test
    void session_cookie_authenticates_debug_echo() {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, "cookie-test"));
        var u = ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> users.save(new UserEntity(
                        UUID.randomUUID(), tenantId, "sub-cookie-test", "c@example.com")));
        minter.mint(u.getGoogleSubject(), u.getEmail());

        RestClient client = RestClient.create("http://localhost:" + port);
        ResponseEntity<String> resp = client.get()
                .uri("/debug/tenant-echo")
                .header(TestSessionSupport.HEADER_SUBJECT, u.getGoogleSubject())
                .header(TestSessionSupport.HEADER_EMAIL, u.getEmail())
                .retrieve()
                .toEntity(String.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isEqualTo(tenantId.toString());
    }
}
