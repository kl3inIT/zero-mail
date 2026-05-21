package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.persistence.AssistantMemoryEntity;
import com.zeromail.core.chat.persistence.AssistantMemoryJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantMemoryService {

    private static final int MAX_CONTENT_LENGTH = 4_000;

    private final AssistantMemoryJpaRepository assistantMemoryRepository;

    public AssistantMemoryService(AssistantMemoryJpaRepository assistantMemoryRepository) {
        this.assistantMemoryRepository = assistantMemoryRepository;
    }

    @Transactional
    public UUID save(UUID tenantId, String content) {
        String normalizedContent = requireBoundedText(content, "content", MAX_CONTENT_LENGTH);
        AssistantMemoryEntity assistantMemory =
                assistantMemoryRepository.saveAndFlush(
                        new AssistantMemoryEntity(
                                UUID.randomUUID(), tenantId, normalizedContent, "chat"));
        return assistantMemory.getMemoryId();
    }

    static String requireBoundedText(String text, String fieldName, int maxLength) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalizedText = text.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        if (normalizedText.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalizedText;
    }
}
