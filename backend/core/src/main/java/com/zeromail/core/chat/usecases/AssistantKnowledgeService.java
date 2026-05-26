package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.exception.KnowledgeSnippetException;
import com.zeromail.core.chat.persistence.AssistantKnowledgeMemoryEntity;
import com.zeromail.core.chat.persistence.AssistantKnowledgeMemoryJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Transactional(readOnly = true)
    public List<KnowledgeSnippet> list(UUID tenantId) {
        return assistantKnowledgeMemoryRepository
                .findAllByTenantIdOrderByUpdatedAtDesc(tenantId)
                .stream()
                .map(AssistantKnowledgeService::toSnippet)
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeSnippet get(UUID tenantId, UUID snippetId) {
        return assistantKnowledgeMemoryRepository
                .findByIdAndTenantId(snippetId, tenantId)
                .map(AssistantKnowledgeService::toSnippet)
                .orElseThrow(KnowledgeSnippetException::notFound);
    }

    @Transactional
    public UUID append(UUID tenantId, String title, String content) {
        String normalizedTitle =
                AssistantMemoryService.requireBoundedText(title, "title", MAX_TITLE_LENGTH);
        String normalizedContent =
                AssistantMemoryService.requireBoundedText(content, "content", MAX_CONTENT_LENGTH);
        try {
            AssistantKnowledgeMemoryEntity knowledgeMemory =
                    assistantKnowledgeMemoryRepository.saveAndFlush(
                            new AssistantKnowledgeMemoryEntity(
                                    UUID.randomUUID(),
                                    tenantId,
                                    normalizedTitle,
                                    normalizedContent));
            return knowledgeMemory.getKnowledgeMemoryId();
        } catch (DataIntegrityViolationException duplicateTitleFailure) {
            throw KnowledgeSnippetException.duplicateTitle();
        }
    }

    @Transactional
    public KnowledgeSnippet update(UUID tenantId, UUID snippetId, String title, String content) {
        AssistantKnowledgeMemoryEntity knowledgeMemory =
                assistantKnowledgeMemoryRepository
                        .findByIdAndTenantId(snippetId, tenantId)
                        .orElseThrow(KnowledgeSnippetException::notFound);
        knowledgeMemory.update(
                AssistantMemoryService.requireBoundedText(title, "title", MAX_TITLE_LENGTH),
                AssistantMemoryService.requireBoundedText(content, "content", MAX_CONTENT_LENGTH));
        try {
            return toSnippet(assistantKnowledgeMemoryRepository.saveAndFlush(knowledgeMemory));
        } catch (DataIntegrityViolationException duplicateTitleFailure) {
            throw KnowledgeSnippetException.duplicateTitle();
        }
    }

    @Transactional
    public void delete(UUID tenantId, UUID snippetId) {
        AssistantKnowledgeMemoryEntity knowledgeMemory =
                assistantKnowledgeMemoryRepository
                        .findByIdAndTenantId(snippetId, tenantId)
                        .orElseThrow(KnowledgeSnippetException::notFound);
        assistantKnowledgeMemoryRepository.delete(knowledgeMemory);
    }

    private static KnowledgeSnippet toSnippet(AssistantKnowledgeMemoryEntity knowledgeMemory) {
        return new KnowledgeSnippet(
                knowledgeMemory.getKnowledgeMemoryId(),
                knowledgeMemory.getTitle(),
                knowledgeMemory.getContent(),
                knowledgeMemory.getUpdatedAt());
    }

    public record KnowledgeSnippet(UUID id, String title, String content, Instant updatedAt) {}
}
