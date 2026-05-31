package com.zeromail.api.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"title", "content"})
public record KnowledgeSnippetRequest(
        @NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 8000) String content) {}
