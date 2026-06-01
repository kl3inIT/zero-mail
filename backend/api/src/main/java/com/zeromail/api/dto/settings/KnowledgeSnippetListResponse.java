package com.zeromail.api.dto.settings;

import com.zeromail.core.chat.usecases.AssistantKnowledgeService.KnowledgeSnippet;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = "items")
public record KnowledgeSnippetListResponse(List<KnowledgeSnippetResponse> items) {

    public KnowledgeSnippetListResponse {
        items = List.copyOf(items);
    }

    public static KnowledgeSnippetListResponse from(List<KnowledgeSnippet> knowledgeSnippets) {
        return new KnowledgeSnippetListResponse(
                knowledgeSnippets.stream().map(KnowledgeSnippetResponse::from).toList());
    }
}
