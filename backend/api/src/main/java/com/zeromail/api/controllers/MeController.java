package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.account.MeResponse;
import com.zeromail.api.dto.account.UpdateLanguageRequest;
import com.zeromail.core.account.model.CurrentUserProjection;
import com.zeromail.core.account.service.AccountService;
import com.zeromail.core.tenant.TenantContext;

import jakarta.validation.Valid;

/**
 * {@code /me} surface for the authenticated principal. Transport-only — all writes are
 * routed through {@link AccountService} so tenant invariants and {@code @Transactional}
 * boundaries live in one place.
 *
 * <p>Phase 1.1 wires the language-preference half of REQ-2: {@code GET /me} now exposes
 * {@code preferredLanguage}, and the new {@code PATCH /me/language} endpoint accepts an
 * allow-listed locale code ({@code "vi"} or {@code "en"}) and persists it through JPA
 * dirty-tracking. The frontend writes the {@code NEXT_LOCALE} cookie locally and then
 * fires this PATCH so the preference survives across sessions and devices (D-B2).
 *
 * <p>This controller never resolves localized prose on the server side —
 * user-facing localization is owned entirely by the frontend dictionary (D-C1).
 */
@RestController
public class MeController {

    private final AccountService accountService;

    public MeController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public MeResponse me() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        CurrentUserProjection user = accountService.requireCurrentUser(tenantId);
        return MeResponse.from(user);
    }

    /**
     * Persist the caller's preferred language. {@code @Valid} engages Bean Validation
     * on {@link UpdateLanguageRequest}; an out-of-allow-list value flows to
     * {@code GlobalExceptionHandler.handleMethodArgumentNotValid} which emits the
     * Phase 1.1 ProblemDetail extension contract (top-level {@code code = error.validation}
     * + {@code fieldErrors[]}). The DB CHECK constraint added in Plan 01 is the second
     * line of defense (D-B2).
     */
    @PatchMapping("/me/language")
    public MeResponse updateLanguage(@Valid @RequestBody UpdateLanguageRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        CurrentUserProjection updated = accountService.updateCurrentUserLanguage(tenantId, req.language());
        return MeResponse.from(updated);
    }
}
