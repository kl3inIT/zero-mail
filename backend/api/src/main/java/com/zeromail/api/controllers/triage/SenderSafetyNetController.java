package com.zeromail.api.controllers.triage;

import com.zeromail.api.dto.triage.ProtectedSendersResponse;
import com.zeromail.api.dto.triage.SenderOptInResponse;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.domain.SenderEmailCanonicalizer;
import com.zeromail.core.triage.service.SenderSafetyNetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "triage")
public class SenderSafetyNetController {

    private static final Logger log = LoggerFactory.getLogger(SenderSafetyNetController.class);

    private final SenderEmailCanonicalizer senderEmailCanonicalizer;
    private final SenderSafetyNetService senderSafetyNetService;

    public SenderSafetyNetController(
            SenderEmailCanonicalizer senderEmailCanonicalizer,
            SenderSafetyNetService senderSafetyNetService) {
        this.senderEmailCanonicalizer = senderEmailCanonicalizer;
        this.senderSafetyNetService = senderSafetyNetService;
    }

    @GetMapping("/api/triage/sender-safety-net")
    public ProtectedSendersResponse listProtectedSenders() {
        return ProtectedSendersResponse.from(
                senderSafetyNetService.listProtectedSenders(currentTenantId()));
    }

    @PostMapping("/api/triage/sender-safety-net/{senderEmail}/opt-in")
    public SenderOptInResponse optIn(@PathVariable String senderEmail) {
        UUID tenantId = currentTenantId();
        String canonicalSenderEmail = senderEmailCanonicalizer.canonicalize(senderEmail);
        String senderEmailHash =
                senderEmailCanonicalizer.redisCacheKeyComponent(canonicalSenderEmail);
        senderSafetyNetService.optInSender(tenantId, canonicalSenderEmail);
        log.info(
                "event=triage_sender_opt_in tenantId={} senderEmailHash={}",
                tenantId,
                senderEmailHash);
        return SenderOptInResponse.from(canonicalSenderEmail, true);
    }

    private static UUID currentTenantId() {
        return UUID.fromString(TenantContext.currentOrThrow());
    }
}
