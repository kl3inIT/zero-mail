package com.zeromail.api.controllers.settings;

import com.zeromail.api.dto.settings.VoiceSettingsResponse;
import com.zeromail.api.dto.settings.VoiceSettingsUpdateRequest;
import com.zeromail.core.chat.usecases.settings.SettingsVoiceCommand;
import com.zeromail.core.chat.usecases.settings.SettingsVoiceService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "settings-voice")
@RequestMapping("/api/settings/voice")
@PreAuthorize("isAuthenticated()")
public class SettingsVoiceController {

    private final SettingsVoiceService settingsVoiceService;

    public SettingsVoiceController(SettingsVoiceService settingsVoiceService) {
        this.settingsVoiceService = settingsVoiceService;
    }

    @GetMapping({"", "/"})
    public VoiceSettingsResponse getVoiceSettings() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return VoiceSettingsResponse.from(settingsVoiceService.get(tenantId));
    }

    @PutMapping({"", "/"})
    public VoiceSettingsResponse updateVoiceSettings(
            @Valid @RequestBody VoiceSettingsUpdateRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return VoiceSettingsResponse.from(
                settingsVoiceService.update(
                        tenantId,
                        new SettingsVoiceCommand(
                                request.writingStyle(),
                                request.personalInstructions(),
                                request.emailSignature(),
                                request.aiOutputLanguage())));
    }
}
