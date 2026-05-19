package com.zeromail.api.controllers.tenant;

import com.zeromail.api.dto.gmail.GmailConnectionStatusResponse;
import com.zeromail.core.gmail.projection.GmailConnectionProjection;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /gmail/connection/status} — returns the current Gmail-connection lifecycle state for
 * the authenticated tenant.
 *
 * <p><b>Phase 1.2.1 rename (D-D4):</b> URL changed from {@code /tenant/status} to {@code
 * /gmail/connection/status} and response renamed from {@code TenantStatusResponse} to {@link
 * GmailConnectionStatusResponse} because the payload has always been Gmail connection state, never
 * tenant-level data. Project is pre-launch — clean break preferred over a deprecated alias (D-D4
 * explicit rejection of transitional path).
 *
 * <p><b>File-name vs class-name carve-out:</b> per CONTEXT line 131 the file remains {@code
 * TenantStatusController.java} (class name unchanged) — the API rename happens at the URL + Tag +
 * DTO surface, not at the internal Java symbol. Future readers: do not rename this file in a
 * follow-up phase without updating Spring's component scan conventions and any documentation that
 * references the class.
 *
 * <p><b>Response mapping:</b> the API DTO owns the static {@code from(...)} factory so controllers
 * stay transport-only without repeating small projection-to-response mappers.
 */
@RestController
@Tag(name = "gmail")
public class TenantStatusController {

    private final GmailConnectionService connectionService;

    public TenantStatusController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/api/gmail/connection/status")
    public GmailConnectionStatusResponse status() {
        UUID tenantId = TenantContext.currentTenantUuid();
        GmailConnectionProjection projection = connectionService.currentStatus(tenantId);
        return GmailConnectionStatusResponse.from(projection);
    }
}
