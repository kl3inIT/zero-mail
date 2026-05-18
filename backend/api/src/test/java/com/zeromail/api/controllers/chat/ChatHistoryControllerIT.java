package com.zeromail.api.controllers.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
@SuppressWarnings("SqlResolve")
class ChatHistoryControllerIT extends ApiPostgresTestBase {

    @LocalServerPort int serverPort;

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;
    @Autowired ChatMessageJdbcRepository chatMessageRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void history_list_detail_soft_delete_and_cross_tenant_access_are_tenant_scoped() {
        SeedData seedData = seedUser("chat-history");
        SeedData otherSeedData = seedUser("chat-history-other");
        UUID oldestChatId =
                seedChat(seedData.tenantId(), "Oldest", Instant.parse("2026-05-18T00:00:00Z"));
        UUID deletedChatId =
                seedChat(seedData.tenantId(), "Delete me", Instant.parse("2026-05-18T00:01:00Z"));
        UUID newestChatId =
                seedChat(seedData.tenantId(), "Newest", Instant.parse("2026-05-18T00:02:00Z"));
        UUID otherTenantChatId =
                seedChat(
                        otherSeedData.tenantId(),
                        "Other tenant",
                        Instant.parse("2026-05-18T00:03:00Z"));
        seedMessage(oldestChatId, seedData.tenantId(), "hello", "Xin chào");
        seedMessage(deletedChatId, seedData.tenantId(), "delete", "Deleted");
        seedMessage(newestChatId, seedData.tenantId(), "new", "Newest");
        seedMessage(otherTenantChatId, otherSeedData.tenantId(), "other", "Other");

        JsonNode initialListJson = getJson(authenticatedClient(seedData), "/api/chat/history");
        assertThat(chatIds(initialListJson))
                .containsExactly(
                        newestChatId.toString(), deletedChatId.toString(), oldestChatId.toString());
        assertThat(initialListJson.toString()).doesNotContain("rename", "search");

        JsonNode detailJson = getJson(authenticatedClient(seedData), "/api/chat/" + oldestChatId);
        assertThat(detailJson.path("messages")).hasSize(2);
        assertThat(detailJson.path("messages").get(1).path("role").asString())
                .isEqualTo("ASSISTANT");

        ResponseEntity<String> deleteResponse =
                authenticatedClient(seedData)
                        .delete()
                        .uri("/api/chat/{chatId}", deletedChatId)
                        .retrieve()
                        .toEntity(String.class);
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);

        JsonNode afterDeleteListJson = getJson(authenticatedClient(seedData), "/api/chat/history");
        assertThat(chatIds(afterDeleteListJson))
                .containsExactly(newestChatId.toString(), oldestChatId.toString());

        ResponseEntity<String> crossTenantResponse =
                authenticatedClient(seedData)
                        .get()
                        .uri("/api/chat/{chatId}", otherTenantChatId)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (_, _) -> {})
                        .toEntity(String.class);
        assertThat(crossTenantResponse.getStatusCode().value()).isEqualTo(404);
    }

    private JsonNode getJson(RestClient restClient, String uri) {
        ResponseEntity<String> response =
                restClient.get().uri(uri).retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return objectMapper.readTree(response.getBody());
    }

    private static List<String> chatIds(JsonNode historyListJson) {
        ArrayList<String> ids = new ArrayList<>();
        historyListJson.path("chats").forEach(chatJson -> ids.add(chatJson.path("id").asString()));
        return List.copyOf(ids);
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

    private UUID seedChat(UUID tenantId, String title, Instant updatedAt) {
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into chat(id, tenant_id, title, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 0)
                """,
                chatId,
                tenantId,
                title,
                Timestamp.from(updatedAt.minusSeconds(30)),
                Timestamp.from(updatedAt));
        return chatId;
    }

    private void seedMessage(UUID chatId, UUID tenantId, String userText, String assistantText) {
        chatMessageRepository.insert(
                new ChatMessage(
                        UUID.randomUUID(),
                        chatId,
                        tenantId,
                        ChatRole.USER.id(),
                        ChatMessageParts.v1(List.of(new TextPart("user-text", userText))),
                        Instant.now()));
        chatMessageRepository.insert(
                new ChatMessage(
                        UUID.randomUUID(),
                        chatId,
                        tenantId,
                        ChatRole.ASSISTANT.id(),
                        ChatMessageParts.v1(
                                List.of(
                                        new AssistantTextPart(
                                                "assistant-text", assistantText, Instant.now()))),
                        Instant.now()));
    }

    private record SeedData(UUID tenantId, String googleSubject, String email) {}
}
