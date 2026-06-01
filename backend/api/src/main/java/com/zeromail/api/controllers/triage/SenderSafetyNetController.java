package com.zeromail.api.controllers.triage;

import com.zeromail.api.dto.triage.ProtectedSendersResponse;
import com.zeromail.api.dto.triage.ProtectedSendersResponse.ProtectedSenderResponse;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "triage")
public class SenderSafetyNetController {

    private final SenderSafetyNetService senderSafetyNetService;

    public SenderSafetyNetController(SenderSafetyNetService senderSafetyNetService) {
        this.senderSafetyNetService = senderSafetyNetService;
    }

    @GetMapping("/api/triage/sender-safety-net")
    public ProtectedSendersResponse listProtectedSenders() {
        return ProtectedSendersResponse.from(
                senderSafetyNetService.listProtectedSenders(TenantContext.currentTenantUuid()));
    }

    @PostMapping("/api/triage/sender-safety-net/{senderEmail}/opt-in")
    public ResponseEntity<ProtectedSenderResponse> optIn(@PathVariable String senderEmail) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ProtectedSenderResponse.from(
                                senderSafetyNetService.optInSender(tenantId, senderEmail)));
    }

    @DeleteMapping("/api/triage/sender-safety-net/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        senderSafetyNetService.deleteByIdAndTenant(TenantContext.currentTenantUuid(), id);
    }
}
