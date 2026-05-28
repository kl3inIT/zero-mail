package com.zeromail.api.controllers.composer;

import com.zeromail.api.dto.composer.ComposerDraftResponseDto;
import com.zeromail.api.dto.composer.ComposerDraftUpsertRequestDto;
import com.zeromail.core.composer.usecases.ComposerDraftService;
import com.zeromail.core.composer.usecases.ComposerDraftSnapshot;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP surface for the inbox composer draft auto-save loop.
 *
 * <p>{@code GET /api/composer/drafts?gmailThreadId=...} → existing draft snapshot or 204 No Content
 * when none is found.
 *
 * <p>{@code PUT /api/composer/drafts} → upsert. The frontend debounces composer keystrokes and
 * calls this endpoint to keep Gmail's draft in sync. Returns the snapshot that landed in Gmail.
 *
 * <p>{@code DELETE /api/composer/drafts/{draftId}} → drop the draft. Called when the user
 * successfully sends or explicitly discards the composer.
 */
@RestController
@Tag(name = "composer-draft")
@RequestMapping("/api/composer/drafts")
public class ComposerDraftController {

    private final ComposerDraftService composerDraftService;

    public ComposerDraftController(ComposerDraftService composerDraftService) {
        this.composerDraftService = composerDraftService;
    }

    @GetMapping
    public ResponseEntity<ComposerDraftResponseDto> find(@RequestParam String gmailThreadId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        try {
            Optional<ComposerDraftSnapshot> snapshot =
                    composerDraftService.find(tenantId, gmailThreadId);
            return snapshot.map(found -> ResponseEntity.ok(ComposerDraftResponseDto.from(found)))
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IOException gmailFailure) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Gmail draft lookup failed", gmailFailure);
        }
    }

    @PutMapping
    public ComposerDraftResponseDto upsert(@RequestBody @Valid ComposerDraftUpsertRequestDto body) {
        UUID tenantId = TenantContext.currentTenantUuid();
        try {
            ComposerDraftSnapshot snapshot =
                    composerDraftService.upsert(tenantId, body.toCommand());
            return ComposerDraftResponseDto.from(snapshot);
        } catch (IOException gmailFailure) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Gmail draft upsert failed", gmailFailure);
        } catch (IllegalArgumentException invalidRequest) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, invalidRequest.getMessage(), invalidRequest);
        }
    }

    @DeleteMapping("/{draftId}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String draftId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        try {
            composerDraftService.delete(tenantId, draftId);
        } catch (IOException gmailFailure) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Gmail draft delete failed", gmailFailure);
        }
    }
}
