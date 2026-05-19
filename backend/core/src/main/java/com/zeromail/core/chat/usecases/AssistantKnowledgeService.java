package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.persistence.AssistantKnowledgeMemoryEntity;
import com.zeromail.core.chat.persistence.AssistantKnowledgeMemoryJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantKnowledgeService {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTENT_LENGTH = 8_000;

    private final AssistantKnowledgeMemoryJpaRepository assistantKnowledgeMemoryRepository;

    public AssistantKnowledgeService(
            AssistantKnowledgeMemoryJpaRepository assistantKnowledgeMemoryRepository) {
        this.assistantKnowledgeMemoryRepository = assistantKnowledgeMemoryRepository;
    }

    @Transactional
    public UUID append(UUID tenantId, String title, String content) {
        String normalizedTitle =
                AssistantMemoryService.requireBoundedText(title, "title", MAX_TITLE_LENGTH);
        String normalizedContent =
                AssistantMemoryService.requireBoundedText(content, "content", MAX_CONTENT_LENGTH);
        AssistantKnowledgeMemoryEntity knowledgeMemory =
                assistantKnowledgeMemoryRepository.saveAndFlush(
                        new AssistantKnowledgeMemoryEntity(
                                UUID.randomUUID(), tenantId, normalizedTitle, normalizedContent));
        return knowledgeMemory.getKnowledgeMemoryId();
    }
}
