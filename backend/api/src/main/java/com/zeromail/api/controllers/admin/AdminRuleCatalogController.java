package com.zeromail.api.controllers.admin;

import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogActionDescriptorWriteRequest;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogActionOrderEntryRequest;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogActionReorderRequest;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogActionsAdminResponse;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogEnabledRequest;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogExampleWriteRequest;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogMutationResponse;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogOrderEntryRequest;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogPersonaWriteRequest;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogPersonasAdminResponse;
import com.zeromail.api.dto.admin.rulecatalog.RuleCatalogReorderRequest;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.rules.catalog.usecases.RuleCatalogAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "admin-rule-catalog")
@RequestMapping("/api/admin/rule-catalog")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRuleCatalogController {

    private final RuleCatalogAdminService ruleCatalogAdminService;

    public AdminRuleCatalogController(RuleCatalogAdminService ruleCatalogAdminService) {
        this.ruleCatalogAdminService =
                Objects.requireNonNull(ruleCatalogAdminService, "ruleCatalogAdminService");
    }

    @GetMapping("/personas")
    public RuleCatalogPersonasAdminResponse personas() {
        AdminContext.currentOrThrow();
        return RuleCatalogPersonasAdminResponse.from(ruleCatalogAdminService.listPersonas());
    }

    @PostMapping("/personas")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleCatalogMutationResponse createPersona(
            @Valid @RequestBody RuleCatalogPersonaWriteRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        return RuleCatalogMutationResponse.created(
                ruleCatalogAdminService.createPersona(
                        request.toCommand(),
                        httpServletRequest.getRemoteAddr(),
                        UUID.randomUUID()));
    }

    @PutMapping("/personas/{personaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePersona(
            @PathVariable UUID personaId,
            @Valid @RequestBody RuleCatalogPersonaWriteRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ruleCatalogAdminService.updatePersona(
                personaId,
                request.toCommand(),
                reasonOrDefault(request.reason(), "update persona"),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PatchMapping("/personas/{personaId}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePersonaEnabled(
            @PathVariable UUID personaId,
            @Valid @RequestBody RuleCatalogEnabledRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ruleCatalogAdminService.setPersonaEnabled(
                personaId,
                request.enabled(),
                request.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PutMapping("/personas/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderPersonas(
            @Valid @RequestBody RuleCatalogReorderRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ruleCatalogAdminService.reorderPersonas(
                request.items().stream().map(RuleCatalogOrderEntryRequest::toCommand).toList(),
                request.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PostMapping("/personas/{personaId}/examples")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleCatalogMutationResponse createExample(
            @PathVariable UUID personaId,
            @Valid @RequestBody RuleCatalogExampleWriteRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        return RuleCatalogMutationResponse.created(
                ruleCatalogAdminService.createPrompt(
                        personaId,
                        request.toCommand(),
                        request.reason(),
                        httpServletRequest.getRemoteAddr(),
                        UUID.randomUUID()));
    }

    @PutMapping("/examples/{exampleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateExample(
            @PathVariable UUID exampleId,
            @Valid @RequestBody RuleCatalogExampleWriteRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ruleCatalogAdminService.updatePrompt(
                exampleId,
                request.toCommand(),
                request.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PatchMapping("/examples/{exampleId}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateExampleEnabled(
            @PathVariable UUID exampleId,
            @Valid @RequestBody RuleCatalogEnabledRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ruleCatalogAdminService.setPromptEnabled(
                exampleId,
                request.enabled(),
                request.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PutMapping("/examples/{personaId}/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderExamples(
            @PathVariable UUID personaId,
            @Valid @RequestBody RuleCatalogReorderRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ruleCatalogAdminService.reorderPrompts(
                personaId,
                request.items().stream().map(RuleCatalogOrderEntryRequest::toCommand).toList(),
                request.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @GetMapping("/actions")
    public RuleCatalogActionsAdminResponse actions() {
        AdminContext.currentOrThrow();
        return RuleCatalogActionsAdminResponse.from(
                ruleCatalogAdminService.listActionDescriptors());
    }

    @PutMapping("/actions/{actionKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertActionDescriptor(
            @PathVariable String actionKey,
            @Valid @RequestBody RuleCatalogActionDescriptorWriteRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ruleCatalogAdminService.upsertActionDescriptor(
                request.toCommand(actionKey),
                reasonOrDefault(request.reason(), "upsert action descriptor"),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PatchMapping("/actions/{actionKey}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateActionDescriptorEnabled(
            @PathVariable String actionKey,
            @Valid @RequestBody RuleCatalogEnabledRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ruleCatalogAdminService.setActionDescriptorEnabled(
                actionKey,
                request.enabled(),
                request.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    @PutMapping("/actions/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderActions(
            @Valid @RequestBody RuleCatalogActionReorderRequest request,
            HttpServletRequest httpServletRequest) {
        AdminContext.currentOrThrow();
        ruleCatalogAdminService.reorderActionDescriptors(
                request.items().stream()
                        .map(RuleCatalogActionOrderEntryRequest::toCommand)
                        .toList(),
                request.reason(),
                httpServletRequest.getRemoteAddr(),
                UUID.randomUUID());
    }

    private static String reasonOrDefault(String reason, String defaultReason) {
        if (reason == null || reason.isBlank()) {
            return defaultReason;
        }
        return reason;
    }
}
