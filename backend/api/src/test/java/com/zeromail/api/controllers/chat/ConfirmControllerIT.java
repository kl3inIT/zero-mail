package com.zeromail.api.controllers.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.chat.exception.PendingActionNotFoundException;
import com.zeromail.core.chat.usecases.ConfirmActionService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class ConfirmControllerIT extends ApiPostgresTestBase {

    @LocalServerPort int serverPort;

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ConfirmActionService confirmActionService;

    @Test
    void confirm_endpoint_delegates_to_confirm_action_service() throws Exception {
        SeedData seedData = seedUser("confirm-wired");
        UUID chatId = UUID.randomUUID();
        when(confirmActionService.confirm(eq(chatId), eq("tool-send-1"), eq(true), anyMap()))
                .thenReturn(
                        new ConfirmActionService.ConfirmActionResult(
                                "CONFIRMED", Map.of("state", "committed")));

        ResponseEntity<String> response =
                authenticatedClient(seedData)
                        .post()
                        .uri("/api/chat/{chatId}/confirm", chatId)
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
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"state\":\"CONFIRMED\"");
    }

    @Test
    void cancel_endpoint_delegates_to_confirm_action_service() throws Exception {
        SeedData seedData = seedUser("confirm-cancel");
        UUID chatId = UUID.randomUUID();
        when(confirmActionService.cancel(chatId, "tool-send-2"))
                .thenReturn(
                        new ConfirmActionService.ConfirmActionResult(
                                "CANCELED", Map.of("state", "canceled")));

        ResponseEntity<String> response =
                authenticatedClient(seedData)
                        .post()
                        .uri("/api/chat/{chatId}/cancel", chatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "toolCallId",
                                                "tool-send-2",
                                                "contentOverride",
                                                Map.of(),
                                                "vipAcknowledged",
                                                false)))
                        .retrieve()
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"state\":\"CANCELED\"");
    }

    @Test
    void missing_pending_action_returns_not_found() throws Exception {
        SeedData seedData = seedUser("confirm-missing");
        UUID chatId = UUID.randomUUID();
        when(confirmActionService.confirm(eq(chatId), eq("missing-tool"), eq(false), anyMap()))
                .thenThrow(new PendingActionNotFoundException("missing-tool"));

        ResponseEntity<String> response =
                authenticatedClient(seedData)
                        .post()
                        .uri("/api/chat/{chatId}/confirm", chatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "toolCallId",
                                                "missing-tool",
                                                "contentOverride",
                                                Map.of(),
                                                "vipAcknowledged",
                                                false)))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (_, _) -> {})
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    private RestClient authenticatedClient(SeedData seedData) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .defaultHeader(TestSessionSupport.HEADER_SUBJECT, seedData.googleSubject())
                .defaultHeader(TestSessionSupport.HEADER_EMAIL, seedData.email())
                .build();
    }

    private SeedData seedUser(String label) {
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
