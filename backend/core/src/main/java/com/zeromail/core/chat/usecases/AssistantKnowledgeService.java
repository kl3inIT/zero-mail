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
    private static final int MAX_CHAT_MEMORY_CONTENT_LENGTH = 4_000;

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
                AssistantTextNormalizer.requireBoundedText(title, "title", MAX_TITLE_LENGTH);
        String normalizedContent =
                AssistantTextNormalizer.requireBoundedText(content, "content", MAX_CONTENT_LENGTH);
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
    public UUID appendChatMemory(UUID tenantId, String content) {
        String normalizedContent =
                AssistantTextNormalizer.requireBoundedText(
                        content, "content", MAX_CHAT_MEMORY_CONTENT_LENGTH);
        return append(tenantId, titleFromChatMemory(normalizedContent), normalizedContent);
    }

    @Transactional
    public KnowledgeSnippet update(UUID tenantId, UUID snippetId, String title, String content) {
        AssistantKnowledgeMemoryEntity knowledgeMemory =
                assistantKnowledgeMemoryRepository
                        .findByIdAndTenantId(snippetId, tenantId)
                        .orElseThrow(KnowledgeSnippetException::notFound);
        knowledgeMemory.update(
                AssistantTextNormalizer.requireBoundedText(title, "title", MAX_TITLE_LENGTH),
                AssistantTextNormalizer.requireBoundedText(content, "content", MAX_CONTENT_LENGTH));
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

    private static String titleFromChatMemory(String content) {
        String normalizedFirstLine =
                content.lines().findFirst().orElse("Chat memory").replaceAll("\\s+", " ").trim();
        String baseTitle = normalizedFirstLine.isBlank() ? "Chat memory" : normalizedFirstLine;
        String uniqueSuffix = " (" + UUID.randomUUID().toString().substring(0, 8) + ")";
        int maximumBaseLength = MAX_TITLE_LENGTH - uniqueSuffix.length();
        if (baseTitle.length() > maximumBaseLength) {
            baseTitle = baseTitle.substring(0, maximumBaseLength).trim();
        }
        return baseTitle + uniqueSuffix;
    }

    public record KnowledgeSnippet(UUID id, String title, String content, Instant updatedAt) {}
}
