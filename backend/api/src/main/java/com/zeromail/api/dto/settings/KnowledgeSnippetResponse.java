package com.zeromail.api.dto.settings;

import com.zeromail.core.chat.usecases.AssistantKnowledgeService.KnowledgeSnippet;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "title", "content", "updatedAt"})
public record KnowledgeSnippetResponse(UUID id, String title, String content, Instant updatedAt) {

    public static KnowledgeSnippetResponse from(KnowledgeSnippet knowledgeSnippet) {
        return new KnowledgeSnippetResponse(
                knowledgeSnippet.id(),
                knowledgeSnippet.title(),
                knowledgeSnippet.content(),
                knowledgeSnippet.updatedAt());
    }
}
