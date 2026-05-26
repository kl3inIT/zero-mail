package com.zeromail.core.chat.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.support.PostgresContainerTest;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * CASA Tier 2 V4.2.1 — chat message reads MUST be tenant-scoped and a chat_id from another tenant
 * MUST be rejected at the DB layer via the composite foreign key on (chat_id, tenant_id).
 */
@SuppressWarnings("SqlResolve")
class ChatMessageJdbcRepositoryTenantScopeTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    private ChatMessageJdbcRepository chatMessageRepository;

    @BeforeEach
    void setUp() {
        ChatPartsJsonConverter chatPartsJsonConverter =
                new ChatPartsJsonConverter(JsonMapper.builder().build());
        chatMessageRepository = new ChatMessageJdbcRepository(jdbcTemplate, chatPartsJsonConverter);
    }

    @Test
    void returns_messages_only_for_matching_tenant_and_chat() {
        SeedData tenantA = seedTenantAndChat("tenant-A");
        SeedData tenantB = seedTenantAndChat("tenant-B");
        ChatMessageParts parts =
                ChatMessageParts.v1(List.of(new TextPart("text-1", "scoped-content")));
        for (int messageIndex = 0; messageIndex < 3; messageIndex++) {
            chatMessageRepository.insert(
                    new ChatMessage(
                            UUID.randomUUID(),
                            tenantA.chatId(),
                            tenantA.tenantId(),
                            "user",
                            parts,
                            Instant.now()));
        }

        List<ChatMessage> ownerView =
                chatMessageRepository.findByChatIdAndTenantIdOrderByCreatedAtAsc(
                        tenantA.tenantId(), tenantA.chatId());
        List<ChatMessage> crossTenantView =
                chatMessageRepository.findByChatIdAndTenantIdOrderByCreatedAtAsc(
                        tenantB.tenantId(), tenantA.chatId());

        assertThat(ownerView).hasSize(3);
        assertThat(crossTenantView)
                .as("cross-tenant access to another tenant's chat must return no rows")
                .isEmpty();
    }

    @Test
    void cross_tenant_insert_is_rejected_by_composite_foreign_key() {
        SeedData tenantA = seedTenantAndChat("fk-tenant-A");
        SeedData tenantB = seedTenantAndChat("fk-tenant-B");
        ChatMessageParts parts =
                ChatMessageParts.v1(List.of(new TextPart("text-fk", "cross-tenant-insert")));

        // Build a chat_message row that points to tenantA's chat but tags it as tenantB's tenant.
        // After changelog 098 applies, the composite FK on (chat_id, tenant_id) → chat(id,
        // tenant_id) MUST raise SQLSTATE 23503.
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO chat_message (id, chat_id, tenant_id, role,"
                                                + " parts, created_at) VALUES (?, ?, ?, ?,"
                                                + " ?::jsonb, ?)",
                                        UUID.randomUUID(),
                                        tenantA.chatId(),
                                        tenantB.tenantId(),
                                        "user",
                                        new ChatPartsJsonConverter(JsonMapper.builder().build())
                                                .toJson(parts),
                                        Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(
                        constraintFailure -> {
                            Throwable cursor = constraintFailure;
                            while (cursor != null) {
                                if (cursor instanceof SQLException sqlException
                                        && "23503".equals(sqlException.getSQLState())) {
                                    return;
                                }
                                cursor = cursor.getCause();
                            }
                            throw new AssertionError(
                                    "Expected SQLSTATE 23503 (foreign key violation) in cause"
                                            + " chain");
                        });
    }

    private SeedData seedTenantAndChat(String label) {
        UUID tenantId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, label);
        jdbcTemplate.update(
                "INSERT INTO chat(id, tenant_id, title) VALUES (?, ?, ?)", chatId, tenantId, label);
        return new SeedData(tenantId, chatId);
    }

    private record SeedData(UUID tenantId, UUID chatId) {}
}
