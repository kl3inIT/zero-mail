package com.zeromail.api.controllers.llm;

import com.zeromail.api.dto.llm.ByokCurrentResponse;
import com.zeromail.api.dto.llm.ByokSaveRequest;
import com.zeromail.api.dto.llm.ByokSaveResponse;
import com.zeromail.api.dto.llm.ByokValidateRequest;
import com.zeromail.api.dto.llm.ByokValidateResponse;
import com.zeromail.core.llm.application.ByokSaveCommand;
import com.zeromail.core.llm.application.ByokSaveResult;
import com.zeromail.core.llm.application.ByokValidateCommand;
import com.zeromail.core.llm.application.ByokValidateResult;
import com.zeromail.core.llm.service.ByokService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "llm-byok")
@RequestMapping("/api/llm/byok")
public class ByokController {

    private final ByokService byokService;

    public ByokController(ByokService byokService) {
        this.byokService = byokService;
    }

    @PostMapping("/validate")
    public ByokValidateResponse validate(@Valid @RequestBody ByokValidateRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        ByokValidateResult result =
                byokService.validate(
                        tenantId,
                        new ByokValidateCommand(
                                request.preset(),
                                request.endpoint(),
                                request.model(),
                                request.apiKey()));
        return ByokValidateResponse.from(result);
    }

    @PostMapping
    public ByokSaveResponse save(@Valid @RequestBody ByokSaveRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        ByokSaveResult result =
                byokService.save(
                        tenantId,
                        new ByokSaveCommand(
                                request.preset(),
                                request.endpoint(),
                                request.model(),
                                request.apiKey()));
        return ByokSaveResponse.from(result);
    }

    @GetMapping
    public ByokCurrentResponse current() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        return byokService
                .current(tenantId)
                .map(ByokCurrentResponse::from)
                .orElse(ByokCurrentResponse.empty());
    }
}
