package com.zeromail.core.chat.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class ChatMessageBodyBanTriggerIT extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void safe_chat_message_parts_insert_successfully() {
        SeedData seedData = seedChat("body-ban-safe");

        insertMessage(
                seedData,
                """
                {"schemaVersion":1,"parts":[{"type":"text","text":"hi"}]}
                """);
    }

    @Test
    void subject_over_limit_is_rejected() {
        SeedData seedData = seedChat("body-ban-subject");
        String longSubject = "a".repeat(201);
        String partsJson =
                """
                {"schemaVersion":1,"parts":[{"type":"text","subject":"%s"}]}
                """
                        .formatted(longSubject);

        assertThatThrownBy(() -> insertMessage(seedData, partsJson))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Chat persistence violation");
    }

    @Test
    void script_signature_is_rejected() {
        SeedData seedData = seedChat("body-ban-script");

        assertThatThrownBy(
                        () ->
                                insertMessage(
                                        seedData,
                                        """
                                        {"schemaVersion":1,"parts":[{"type":"text","text":"<script>alert(1)</script>"}]}
                                        """))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Chat persistence violation");
    }

    private void insertMessage(SeedData seedData, String partsJson) {
        jdbcTemplate.update(
                """
                insert into chat_message(id, chat_id, tenant_id, role, parts)
                values (?, ?, ?, 'assistant', ?::jsonb)
                """,
                UUID.randomUUID(),
                seedData.chatId(),
                seedData.tenantId(),
                partsJson);
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
