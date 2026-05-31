package com.zeromail.api.controllers.settings;

import com.zeromail.api.dto.settings.BehaviorSettingsResponse;
import com.zeromail.api.dto.settings.BehaviorSettingsUpdateRequest;
import com.zeromail.core.chat.usecases.settings.SettingsBehaviorCommand;
import com.zeromail.core.chat.usecases.settings.SettingsBehaviorService;
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
@Tag(name = "settings-behavior")
@RequestMapping("/api/settings/behavior")
@PreAuthorize("isAuthenticated()")
public class SettingsBehaviorController {

    private final SettingsBehaviorService settingsBehaviorService;

    public SettingsBehaviorController(SettingsBehaviorService settingsBehaviorService) {
        this.settingsBehaviorService = settingsBehaviorService;
    }

    @GetMapping({"", "/"})
    public BehaviorSettingsResponse getBehaviorSettings() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return BehaviorSettingsResponse.from(settingsBehaviorService.get(tenantId));
    }

    @PutMapping({"", "/"})
    public BehaviorSettingsResponse updateBehaviorSettings(
            @Valid @RequestBody BehaviorSettingsUpdateRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return BehaviorSettingsResponse.from(
                settingsBehaviorService.update(
                        tenantId,
                        new SettingsBehaviorCommand(
                                request.autoDraftReplies(),
                                request.draftConfidence(),
                                request.sensitiveDataProtection())));
    }
}
