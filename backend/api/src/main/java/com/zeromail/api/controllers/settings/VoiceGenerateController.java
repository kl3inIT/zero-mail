package com.zeromail.api.controllers.settings;

import com.zeromail.api.dto.settings.GenerateFromSentRequest;
import com.zeromail.api.dto.settings.GenerateFromSentResponse;
import com.zeromail.core.chat.usecases.settings.VoiceGenerationService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "settings-voice")
@RequestMapping("/api/settings/voice")
@PreAuthorize("isAuthenticated()")
public class VoiceGenerateController {

    private final VoiceGenerationService voiceGenerationService;

    public VoiceGenerateController(VoiceGenerationService voiceGenerationService) {
        this.voiceGenerationService = voiceGenerationService;
    }

    @PostMapping("/generate-from-sent")
    public GenerateFromSentResponse generateFromSent(
            @Valid @RequestBody(required = false) GenerateFromSentRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        int sampleSize = request == null ? 20 : request.sampleSizeOrDefault();
        return GenerateFromSentResponse.from(voiceGenerationService.generate(tenantId, sampleSize));
    }
}
