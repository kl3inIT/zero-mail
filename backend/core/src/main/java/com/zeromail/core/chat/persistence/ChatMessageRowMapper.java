package com.zeromail.core.chat.persistence;

import com.zeromail.core.chat.domain.ChatId;
import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatMessageId;
import com.zeromail.core.chat.domain.ChatRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

public class ChatMessageRowMapper implements RowMapper<ChatMessage> {

    private final ChatPartsJsonConverter chatPartsJsonConverter;

    public ChatMessageRowMapper(ChatPartsJsonConverter chatPartsJsonConverter) {
        this.chatPartsJsonConverter = chatPartsJsonConverter;
    }

    @Override
    public ChatMessage mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ChatMessage(
                new ChatMessageId(resultSet.getObject("id", UUID.class)),
                new ChatId(resultSet.getObject("chat_id", UUID.class)),
                resultSet.getObject("tenant_id", UUID.class).toString(),
                ChatRole.fromId(resultSet.getString("role")),
                chatPartsJsonConverter.fromJson(resultSet.getString("parts")),
                resultSet.getTimestamp("created_at").toInstant());
    }
}
