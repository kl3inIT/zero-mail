package com.zeromail.api.controllers.rules;

import com.zeromail.api.dto.rules.CopyRulesRequest;
import com.zeromail.api.dto.rules.CopyRulesResponse;
import com.zeromail.core.rules.usecases.CopyRulesService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "rules")
@RequestMapping("/api/rules")
public class CopyRulesController {

    private final CopyRulesService copyRulesService;

    public CopyRulesController(CopyRulesService copyRulesService) {
        this.copyRulesService = copyRulesService;
    }

    @PostMapping("/copy")
    public CopyRulesResponse copyRules(@Valid @RequestBody CopyRulesRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return CopyRulesResponse.from(
                copyRulesService.copyRules(
                        tenantId,
                        request.sourceGmailConnectionId(),
                        request.targetGmailConnectionId()));
    }
}
