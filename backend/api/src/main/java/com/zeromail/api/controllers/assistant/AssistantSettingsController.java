package com.zeromail.api.controllers.assistant;

import com.zeromail.api.dto.assistant.AssistantSettingsResponseDto;
import com.zeromail.api.dto.assistant.AssistantSettingsUpdateRequestDto;
import com.zeromail.core.chat.usecases.AssistantSettingsService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read / write the tenant's assistant writing profile. The chat orchestrator and inbox composer
 * Generate both share the same {@code personal_instructions} + {@code writing_style} columns
 * injected via {@code XmlFencedPersonalizationRenderer}, so a single round-trip here lights up both
 * surfaces.
 */
@RestController
@Tag(name = "assistant-settings")
@RequestMapping("/api/assistant/settings")
public class AssistantSettingsController {

    private final AssistantSettingsService assistantSettingsService;

    public AssistantSettingsController(AssistantSettingsService assistantSettingsService) {
        this.assistantSettingsService = assistantSettingsService;
    }

    @GetMapping
    public AssistantSettingsResponseDto read() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return AssistantSettingsResponseDto.from(assistantSettingsService.read(tenantId));
    }

    @PutMapping
    public AssistantSettingsResponseDto update(
            @RequestBody @Valid AssistantSettingsUpdateRequestDto requestBody) {
        UUID tenantId = TenantContext.currentTenantUuid();
        try {
            return AssistantSettingsResponseDto.from(
                    assistantSettingsService.update(tenantId, requestBody.toProfile()));
        } catch (IllegalArgumentException invalidRequest) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, invalidRequest.getMessage(), invalidRequest);
        }
    }
}
