package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.MeResponse;
import com.zeromail.api.dto.UpdateLanguageRequest;
import com.zeromail.core.account.AccountService;
import com.zeromail.core.account.CurrentUserView;
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
        CurrentUserView user = accountService.requireCurrentUser(tenantId);
        return toResponse(user);
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
        CurrentUserView updated = accountService.updateCurrentUserLanguage(tenantId, req.language());
        return toResponse(updated);
    }

    private static MeResponse toResponse(CurrentUserView user) {
        return new MeResponse(
                user.userId().toString(),
                user.tenantId().toString(),
                user.email(),
                user.onboardingStep(),
                user.preferredLanguage());
    }
}
