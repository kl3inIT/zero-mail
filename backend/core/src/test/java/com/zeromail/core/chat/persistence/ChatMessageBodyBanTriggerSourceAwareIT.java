package com.zeromail.core.chat.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class ChatMessageBodyBanTriggerSourceAwareIT extends PostgresContainerTest {

    private static final List<String> EMAIL_READ_TYPES =
            List.of("tool-getMessage", "tool-searchInbox", "tool-getThread", "tool-listLabels");
    private static final List<String> DRAFT_TYPES =
            List.of("tool-sendEmail", "tool-replyEmail", "tool-forwardEmail", "tool-saveDraft");
    private static final List<String> BODY_FIELD_NAMES = List.of("body", "htmlBody", "bodyText");

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void email_read_tool_outputs_with_body_fields_are_rejected() {
        for (String toolType : EMAIL_READ_TYPES) {
            for (String bodyFieldName : BODY_FIELD_NAMES) {
                SeedData seedData = seedChat("source-aware-reject");
                assertThatThrownBy(
                                () ->
                                        insertMessage(
                                                seedData,
                                                toolEnvelope(
                                                        toolType, bodyFieldName, "secret body")))
                        .isInstanceOf(DataAccessException.class)
                        .hasMessageContaining("Chat persistence violation");
            }
        }
    }

    @Test
    void send_and_draft_tool_args_with_body_fields_are_allowed() {
        for (String toolType : DRAFT_TYPES) {
            for (String bodyFieldName : BODY_FIELD_NAMES) {
                SeedData seedData = seedChat("source-aware-allow");
                assertThatCode(
                                () ->
                                        insertMessage(
                                                seedData,
                                                toolEnvelope(
                                                        toolType,
                                                        bodyFieldName,
                                                        "user-authored draft")))
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    void draft_body_length_is_not_enforced_by_layer_three_trigger() {
        SeedData seedData = seedChat("source-aware-long-draft");
        assertThatCode(
                        () ->
                                insertMessage(
                                        seedData,
                                        toolEnvelope("tool-sendEmail", "body", "a".repeat(5000))))
                .doesNotThrowAnyException();
    }

    @Test
    void old_read_email_tool_name_falls_through_to_conservative_unknown_tool_ban() {
        SeedData seedData = seedChat("source-aware-old-name");
        assertThatThrownBy(
                        () ->
                                insertMessage(
                                        seedData, toolEnvelope("tool-readEmail", "body", "secret")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Chat persistence violation");
    }

    private static String toolEnvelope(String toolType, String bodyFieldName, String bodyValue) {
        return """
                {"schemaVersion":1,"parts":[{"type":"%s","toolCallId":"tool-1","state":"output-available","output":{"%s":"%s"}}]}
                """
                .formatted(toolType, bodyFieldName, bodyValue);
    }

    private void insertMessage(SeedData seedData, String partsJson) {
        jdbcTemplate.update(
                """
                insert into chat_message(id, chat_id, tenant_id, role, parts)
                values (?, ?, ?, 'tool', ?::jsonb)
                """,
                UUID.randomUUID(),
                seedData.chatId(),
                seedData.tenantId(),
                partsJson);
    }

    private SeedData seedChat(String label) {
        UUID tenantId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                label + "-" + UUID.randomUUID());
        jdbcTemplate.update(
                "insert into chat(id, tenant_id, title) values (?, ?, ?)", chatId, tenantId, label);
        return new SeedData(tenantId, chatId);
    }

    private record SeedData(UUID tenantId, UUID chatId) {}
}
