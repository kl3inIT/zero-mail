package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.exception.ChatNotFoundException;
import com.zeromail.core.chat.persistence.lowlevel.ChatHistoryRepository;
import com.zeromail.core.chat.projection.ChatHistoryDetail;
import com.zeromail.core.chat.projection.ChatHistoryProjection;
import com.zeromail.core.chat.projection.ChatHistoryProjector;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatHistoryService {

    private final ChatHistoryProjector chatHistoryProjector;
    private final ChatProperties chatProperties;
    private final ChatHistoryRepository chatHistoryRepository;

    public ChatHistoryService(
            ChatHistoryProjector chatHistoryProjector,
            ChatProperties chatProperties,
            ChatHistoryRepository chatHistoryRepository) {
        this.chatHistoryProjector =
                Objects.requireNonNull(
                        chatHistoryProjector, "chatHistoryProjector must not be null");
        this.chatProperties =
                Objects.requireNonNull(chatProperties, "chatProperties must not be null");
        this.chatHistoryRepository =
                Objects.requireNonNull(
                        chatHistoryRepository, "chatHistoryRepository must not be null");
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryProjection> listForCurrentTenant(Integer pageSize, Integer pageOffset) {
        int effectivePageSize =
                pageSize == null || pageSize < 1 ? chatProperties.history().pageSize() : pageSize;
        int effectivePageOffset = pageOffset == null || pageOffset < 0 ? 0 : pageOffset;
        return chatHistoryProjector.listForTenant(
                TenantContext.currentTenantUuid(), effectivePageSize, effectivePageOffset);
    }

    @Transactional(readOnly = true)
    public ChatHistoryDetail loadConversation(UUID chatId) {
        ChatHistoryDetail detail =
                chatHistoryProjector.project(TenantContext.currentTenantUuid(), chatId);
        if (detail == null) {
            throw new ChatNotFoundException(chatId);
        }
        return detail;
    }

    @Transactional
    public void softDelete(UUID chatId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        int updatedRows = chatHistoryRepository.softDeleteChat(tenantId, chatId);
        if (updatedRows == 1) {
            return;
        }
        // updatedRows == 0: either the chat does not exist for this tenant, or it is already
        // soft-deleted. Distinguish the two cases so a retry on a transient network failure
        // does not surface as 404 -- soft delete is idempotent by definition (WR-06).
        if (!chatHistoryRepository.chatExists(tenantId, chatId)) {
            throw new ChatNotFoundException(chatId);
        }
        // Chat exists but was already soft-deleted; treat as idempotent success (204 No Content).
    }
}
