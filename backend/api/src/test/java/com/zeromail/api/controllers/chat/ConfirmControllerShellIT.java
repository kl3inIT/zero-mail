package com.zeromail.api.controllers.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class ConfirmControllerShellIT extends ApiPostgresTestBase {

    @LocalServerPort int serverPort;

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;
    @Autowired ObjectMapper objectMapper;

    @Test
    void confirm_endpoint_remains_not_implemented_until_wave_four_executor_flip() {
        SeedData seedData = seedUser();

        ResponseEntity<String> response =
                authenticatedClient(seedData)
                        .post()
                        .uri("/api/chat/{chatId}/confirm", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "toolCallId",
                                                "tool-send-1",
                                                "contentOverride",
                                                Map.of(),
                                                "vipAcknowledged",
                                                true)))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(501);
        assertThat(response.getBody()).contains("Confirm endpoint ships in Wave 4");
    }

    private RestClient authenticatedClient(SeedData seedData) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .defaultHeader(TestSessionSupport.HEADER_SUBJECT, seedData.googleSubject())
                .defaultHeader(TestSessionSupport.HEADER_EMAIL, seedData.email())
                .build();
    }

    private SeedData seedUser() {
        String label = "confirm-shell";
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, label));
        String googleSubject = "sub-" + label;
        String email = label + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                userRepository.save(
                                        new UserEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                googleSubject,
                                                email)));
        testSessionMinter.mint(googleSubject, email);
        return new SeedData(tenantId, googleSubject, email);
    }

    private record SeedData(UUID tenantId, String googleSubject, String email) {}
}
