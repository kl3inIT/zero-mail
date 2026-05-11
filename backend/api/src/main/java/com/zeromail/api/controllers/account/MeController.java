package com.zeromail.api.controllers.account;

import com.zeromail.api.dto.account.MeResponse;
import com.zeromail.api.dto.account.UpdateLanguageRequest;
import com.zeromail.core.account.projection.CurrentUserProjection;
import com.zeromail.core.account.service.AccountService;
import com.zeromail.core.gmail.projection.GmailConnectionProjection;
import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.service.TenantService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /me} surface for the authenticated principal. Transport-only — all writes are routed
 * through {@link AccountService} so tenant invariants and {@code @Transactional} boundaries live in
 * one place.
 *
 * <p>Phase 1.1 wires the language-preference half of REQ-2: {@code GET /me} now exposes {@code
 * preferredLanguage}, and the new {@code PATCH /me/language} endpoint accepts an allow-listed
 * locale code ({@code "vi"} or {@code "en"}) and persists it through JPA dirty-tracking. The
 * frontend writes the {@code NEXT_LOCALE} cookie locally and then fires this PATCH so the
 * preference survives across sessions and devices (D-B2).
 *
 * <p>This controller never resolves localized prose on the server side — user-facing localization
 * is owned entirely by the frontend dictionary (D-C1).
 */
@RestController
public class MeController {

    private final AccountService accountService;
    private final TenantService tenantService;
    private final GmailConnectionService gmailConnectionService;

    public MeController(
            AccountService accountService,
            TenantService tenantService,
            GmailConnectionService gmailConnectionService) {
        this.accountService = accountService;
        this.tenantService = tenantService;
        this.gmailConnectionService = gmailConnectionService;
    }

    @GetMapping("/me")
    public MeResponse me() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        CurrentUserProjection user = accountService.requireCurrentUser(tenantId);
        boolean triagePaused = tenantService.isTriagePaused(tenantId);
        GmailConnectionProjection gmailConnection = gmailConnectionService.currentStatus(tenantId);
        return MeResponse.from(user, triagePaused, gmailConnection);
    }

    /**
     * Persist the caller's preferred language. {@code @Valid} engages Bean Validation on {@link
     * UpdateLanguageRequest}; an out-of-allow-list value flows to {@code
     * GlobalExceptionHandler.handleMethodArgumentNotValid} which emits the Phase 1.1 ProblemDetail
     * extension contract (top-level {@code code = error.validation} + {@code fieldErrors[]}). The
     * DB CHECK constraint added in Plan 01 is the second line of defense (D-B2).
     */
    @PatchMapping("/me/language")
    public MeResponse updateLanguage(@Valid @RequestBody UpdateLanguageRequest request) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        CurrentUserProjection updated =
                accountService.updateCurrentUserLanguage(tenantId, request.language());
        boolean triagePaused = tenantService.isTriagePaused(tenantId);
        GmailConnectionProjection gmailConnection = gmailConnectionService.currentStatus(tenantId);
        return MeResponse.from(updated, triagePaused, gmailConnection);
    }
}
