package com.zeromail.api.controllers.settings;

import com.zeromail.api.dto.settings.KnowledgeSnippetListResponse;
import com.zeromail.api.dto.settings.KnowledgeSnippetRequest;
import com.zeromail.api.dto.settings.KnowledgeSnippetResponse;
import com.zeromail.core.chat.usecases.AssistantKnowledgeService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "knowledge-snippets")
@RequestMapping("/api/knowledge-snippets")
@PreAuthorize("isAuthenticated()")
public class KnowledgeSnippetController {

    private final AssistantKnowledgeService assistantKnowledgeService;

    public KnowledgeSnippetController(AssistantKnowledgeService assistantKnowledgeService) {
        this.assistantKnowledgeService = assistantKnowledgeService;
    }

    @GetMapping({"", "/"})
    public KnowledgeSnippetListResponse list() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return KnowledgeSnippetListResponse.from(assistantKnowledgeService.list(tenantId));
    }

    @PostMapping({"", "/"})
    public ResponseEntity<KnowledgeSnippetResponse> create(
            @Valid @RequestBody KnowledgeSnippetRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        UUID snippetId =
                assistantKnowledgeService.append(tenantId, request.title(), request.content());
        return ResponseEntity.created(URI.create("/api/knowledge-snippets/" + snippetId))
                .body(
                        KnowledgeSnippetResponse.from(
                                assistantKnowledgeService.get(tenantId, snippetId)));
    }

    @PutMapping("/{snippetId}")
    public KnowledgeSnippetResponse update(
            @PathVariable UUID snippetId, @Valid @RequestBody KnowledgeSnippetRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return KnowledgeSnippetResponse.from(
                assistantKnowledgeService.update(
                        tenantId, snippetId, request.title(), request.content()));
    }

    @DeleteMapping("/{snippetId}")
    public ResponseEntity<Void> delete(@PathVariable UUID snippetId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        assistantKnowledgeService.delete(tenantId, snippetId);
        return ResponseEntity.noContent().build();
    }
}
