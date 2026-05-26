package com.zeromail.api.controllers.byok;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.llm.byok.ByokRateLimiter;
import com.zeromail.core.llm.byok.HostResolver;
import com.zeromail.core.llm.gateway.springai.ProviderConnectionTester;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
abstract class ByokControllerApiTestSupport extends ApiPostgresTestBase {

    @LocalServerPort int serverPort;

    @Autowired TenantRepository tenantRepository;

    @Autowired UserRepository userRepository;

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired ObjectMapper objectMapper;

    @MockitoBean HostResolver hostResolver;

    @MockitoBean ProviderConnectionTester providerConnectionTester;

    @MockitoBean ByokRateLimiter byokRateLimiter;

    @BeforeEach
    void cleanByokRowsAndResetMocks() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE user_byok_key RESTART IDENTITY CASCADE");
        reset(hostResolver, providerConnectionTester, byokRateLimiter);
        when(hostResolver.resolve(anyString())).thenReturn(addresses("8.8.8.8"));
    }

    RestClient authenticatedClient(Seed seed) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .defaultHeader(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())
                .defaultHeader(TestSessionSupport.HEADER_EMAIL, seed.email())
                .build();
    }

    JsonNode postJson(RestClient restClient, String uri, Object body) throws Exception {
        ResponseEntity<String> response = postResponse(restClient, uri, body);
        return objectMapper.readTree(response.getBody());
    }

    ResponseEntity<String> postResponse(RestClient restClient, String uri, Object body) {
        return restClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, _) -> {})
                .toEntity(String.class);
    }

    JsonNode putJson(RestClient restClient, String uri, Object body) throws Exception {
        ResponseEntity<String> response = putResponse(restClient, uri, body);
        return objectMapper.readTree(response.getBody());
    }

    ResponseEntity<String> putResponse(RestClient restClient, String uri, Object body) {
        return restClient
                .put()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, _) -> {})
                .toEntity(String.class);
    }

    ResponseEntity<String> getResponse(RestClient restClient, String uri) {
        return restClient
                .get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, _) -> {})
                .toEntity(String.class);
    }

    void saveByok(RestClient restClient, String provider, String baseUrl, String apiKey) {
        postResponse(
                restClient,
                "/api/byok",
                Map.of("provider", provider, "baseUrl", baseUrl, "apiKey", apiKey));
    }

    Seed seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        String uniqueLabel = label + "-" + UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, uniqueLabel));
        String googleSubject = "sub-" + uniqueLabel;
        String email = uniqueLabel + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                userRepository.save(
                                        new UserEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                googleSubject,
                                                email)));
        return new Seed(tenantId, googleSubject, email);
    }

    static InetAddress[] addresses(String... hosts) throws Exception {
        InetAddress[] addresses = new InetAddress[hosts.length];
        for (int hostIndex = 0; hostIndex < hosts.length; hostIndex++) {
            addresses[hostIndex] = InetAddress.getByName(hosts[hostIndex]);
        }
        return addresses;
    }

    record Seed(UUID tenantId, String googleSubject, String email) {}
}
