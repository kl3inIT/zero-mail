package com.zeromail.api.controllers.settings;

import com.zeromail.api.dto.settings.AiCostResponse;
import com.zeromail.core.llm.cost.AiCostQueryService;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "settings-ai-cost")
@RequestMapping("/api/settings/ai/cost")
@PreAuthorize("isAuthenticated()")
public class SettingsAiCostController {

    private static final String SUPPORTED_WINDOW = "7d";

    private final AiCostQueryService aiCostQueryService;

    public SettingsAiCostController(AiCostQueryService aiCostQueryService) {
        this.aiCostQueryService = aiCostQueryService;
    }

    @GetMapping({"", "/"})
    public AiCostResponse cost(@RequestParam(defaultValue = SUPPORTED_WINDOW) String window) {
        if (!SUPPORTED_WINDOW.equals(window)) {
            throw new AiCostWindowInvalidException();
        }
        UUID tenantId = TenantContext.currentTenantUuid();
        return new AiCostResponse(aiCostQueryService.totalUsdLast7Days(tenantId));
    }

    public static class AiCostWindowInvalidException extends BusinessException {

        @Override
        public ErrorClass errorClass() {
            return ErrorClass.BAD_REQUEST;
        }

        @Override
        public String errorCode() {
            return "ai.cost.window_invalid";
        }

        @Override
        public String logEvent() {
            return "ai_cost_window_invalid";
        }

        @Override
        public String title() {
            return "Invalid AI cost window";
        }

        @Override
        public String detail() {
            return "Only the 7d AI cost window is supported.";
        }
    }
}
