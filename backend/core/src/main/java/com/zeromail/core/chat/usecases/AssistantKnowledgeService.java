package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.persistence.AssistantKnowledgeSnippetEntity;
import com.zeromail.core.chat.persistence.AssistantKnowledgeSnippetJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantKnowledgeService {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTENT_LENGTH = 8_000;

    private final AssistantKnowledgeSnippetJpaRepository assistantKnowledgeSnippetRepository;

    public AssistantKnowledgeService(
            AssistantKnowledgeSnippetJpaRepository assistantKnowledgeSnippetRepository) {
        this.assistantKnowledgeSnippetRepository = assistantKnowledgeSnippetRepository;
    }

    @Transactional
    public UUID append(UUID tenantId, String title, String content) {
        String normalizedTitle =
                AssistantMemoryService.requireBoundedText(title, "title", MAX_TITLE_LENGTH);
        String normalizedContent =
                AssistantMemoryService.requireBoundedText(content, "content", MAX_CONTENT_LENGTH);
        AssistantKnowledgeSnippetEntity knowledgeSnippet =
                assistantKnowledgeSnippetRepository.saveAndFlush(
                        new AssistantKnowledgeSnippetEntity(
                                UUID.randomUUID(), tenantId, normalizedTitle, normalizedContent));
        return knowledgeSnippet.getKnowledgeSnippetId();
    }
}
