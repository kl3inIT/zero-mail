package com.zeromail.core.chat.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.exception.BodyContentBanViolationException;
import com.zeromail.core.support.PostgresContainerTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings("SqlResolve")
class ChatMessageJdbcRepositoryIT extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    private ChatPartsJsonConverter converter;
    private ChatMessageJdbcRepository repository;

    @BeforeEach
    void setUp() {
        converter = new ChatPartsJsonConverter(JsonMapper.builder().build());
        repository = new ChatMessageJdbcRepository(jdbcTemplate, converter);
    }

    @Test
    void inserts_and_reloads_chat_message_parts_with_schema_version() {
        SeedData seedData = seedChat("jdbc-round-trip");
        ChatMessageParts parts =
                ChatMessageParts.v1(
                        List.of(
                                new TextPart("text-1", "Xin chào"),
                                new ToolCallPart(
                                        "tool-1",
                                        "toolcall-labels",
                                        "listLabels",
                                        "input-available",
                                        Map.of(),
                                        false)));

        UUID messageId =
                repository.insert(
                        new ChatMessage(
                                UUID.randomUUID(),
                                seedData.chatId(),
                                seedData.tenantId(),
                                "assistant",
                                parts,
                                Instant.now()));

        ChatMessage reloadedMessage = repository.findById(messageId).orElseThrow();
        assertThat(reloadedMessage.parts().schemaVersion()).isEqualTo(1);
        assertThat(converter.toJson(reloadedMessage.parts())).contains("\"schemaVersion\":1");
    }

    @Test
    void converter_reads_all_v1_fixtures() throws Exception {
        for (String fixtureName :
                List.of(
                        "text-only.json",
                        "single-tool-call.json",
                        "multi-tool-call-confirmed-send.json",
                        "send-email-with-draft-body.json")) {
            assertThat(converter.fromJson(fixture(fixtureName)).schemaVersion()).isEqualTo(1);
        }
    }

    @Test
    void bypassing_sanitizer_body_ban_raises_domain_exception() {
        SeedData seedData = seedChat("jdbc-body-ban");
        ChatMessageParts unsafeParts =
                ChatMessageParts.v1(
                        List.of(
                                new ToolOutputPart(
                                        "tool-unsafe",
                                        "toolcall-unsafe",
                                        "getMessage",
                                        "output-available",
                                        Map.of("htmlBody", "<p>secret</p>"),
                                        false)));

        assertThatThrownBy(
                        () ->
                                repository.insert(
                                        new ChatMessage(
                                                UUID.randomUUID(),
                                                seedData.chatId(),
                                                seedData.tenantId(),
                                                "tool",
                                                unsafeParts,
                                                Instant.now())))
                .isInstanceOf(BodyContentBanViolationException.class);
    }

    private String fixture(String fixtureName) throws Exception {
        try (var fixtureStream =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream("chat-message-fixtures/v1/" + fixtureName)) {
            assertThat(fixtureStream).isNotNull();
            return new String(fixtureStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private SeedData seedChat(String label) {
        UUID tenantId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update("insert into tenants(id, display_name) values (?, ?)", tenantId, label);
        jdbcTemplate.update(
                "insert into chat(id, tenant_id, title) values (?, ?, ?)", chatId, tenantId, label);
        return new SeedData(tenantId, chatId);
    }

    private record SeedData(UUID tenantId, UUID chatId) {}
}
