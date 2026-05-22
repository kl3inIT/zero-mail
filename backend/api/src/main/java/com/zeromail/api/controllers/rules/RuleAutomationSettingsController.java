package com.zeromail.api.controllers.rules;

import com.zeromail.api.dto.rules.RuleAutomationSettingsResponse;
import com.zeromail.api.dto.rules.RuleAutomationSettingsUpdateRequest;
import com.zeromail.core.rules.usecases.RuleAutomationSettingsService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "rules")
@RequestMapping("/api/rules/settings/automation")
public class RuleAutomationSettingsController {

    private final RuleAutomationSettingsService ruleAutomationSettingsService;

    public RuleAutomationSettingsController(
            RuleAutomationSettingsService ruleAutomationSettingsService) {
        this.ruleAutomationSettingsService =
                Objects.requireNonNull(
                        ruleAutomationSettingsService, "ruleAutomationSettingsService");
    }

    @GetMapping
    public ResponseEntity<RuleAutomationSettingsResponse> getSettings() {
        UUID tenantId = TenantContext.currentTenantUuid();
        RuleAutomationSettingsResponse response =
                RuleAutomationSettingsResponse.from(
                        ruleAutomationSettingsService.getOrCreate(tenantId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PutMapping
    public RuleAutomationSettingsResponse updateSettings(
            @Valid @RequestBody RuleAutomationSettingsUpdateRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return RuleAutomationSettingsResponse.from(
                ruleAutomationSettingsService.update(tenantId, request.autoSendRulesEnabled()));
    }
}
