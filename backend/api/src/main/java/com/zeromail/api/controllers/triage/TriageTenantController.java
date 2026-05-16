package com.zeromail.api.controllers.triage;

import com.zeromail.api.dto.triage.TriageShadowModeRequest;
import com.zeromail.api.dto.triage.TriageShadowModeResponse;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.usecases.TenantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "tenant")
public class TriageTenantController {

    private static final Logger log = LoggerFactory.getLogger(TriageTenantController.class);

    private final TenantService tenantService;

    public TriageTenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/api/tenant/triage/shadow-mode")
    public TriageShadowModeResponse getShadowMode() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return TriageShadowModeResponse.from(tenantService.isTriageShadowMode(tenantId));
    }

    @PatchMapping("/api/tenant/triage/shadow-mode")
    public TriageShadowModeResponse setShadowMode(
            @RequestBody @Valid TriageShadowModeRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        tenantService.setTriageShadowMode(tenantId, request.enabled());
        log.info(
                "event=triage_shadow_mode_toggled tenantId={} enabled={}",
                tenantId,
                request.enabled());
        return TriageShadowModeResponse.from(request.enabled());
    }
}
