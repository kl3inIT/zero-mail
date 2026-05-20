package com.zeromail.core.chat.projection;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.chat.persistence.lowlevel.ChatHistoryRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryProjector {

    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatMessageJdbcRepository chatMessageRepository;

    public ChatHistoryProjector(
            ChatHistoryRepository chatHistoryRepository,
            ChatMessageJdbcRepository chatMessageRepository) {
        this.chatHistoryRepository =
                Objects.requireNonNull(
                        chatHistoryRepository, "chatHistoryRepository must not be null");
        this.chatMessageRepository =
                Objects.requireNonNull(
                        chatMessageRepository, "chatMessageRepository must not be null");
    }

    public List<ChatHistoryProjection> listForTenant(UUID tenantId, int pageSize, int pageOffset) {
        return chatHistoryRepository.listForTenant(tenantId, pageSize, pageOffset);
    }

    public ChatHistoryDetail project(UUID tenantId, UUID chatId) {
        ChatHistoryRepository.ChatHeader chatHeader =
                chatHistoryRepository.findChatHeader(tenantId, chatId).orElse(null);
        if (chatHeader == null) {
            return null;
        }
        List<ChatMessageProjection> messages =
                chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId).stream()
                        .filter(chatMessage -> chatMessage.tenantId().equals(tenantId.toString()))
                        .map(ChatHistoryProjector::toProjection)
                        .toList();
        return new ChatHistoryDetail(
                chatHeader.id(),
                chatHeader.title(),
                chatHeader.createdAt(),
                chatHeader.updatedAt(),
                messages);
    }

    private static ChatMessageProjection toProjection(ChatMessage chatMessage) {
        return new ChatMessageProjection(
                chatMessage.id().value(),
                chatMessage.role(),
                chatMessage.parts(),
                chatMessage.createdAt());
    }
}
