package com.zeromail.api.controllers.calendar;

import com.zeromail.api.dto.calendar.MailboxCalendarPreferenceResponse;
import com.zeromail.api.dto.calendar.UpdateMailboxCalendarPreferenceRequest;
import com.zeromail.core.calendar.usecases.MailboxCalendarPreferenceService;
import com.zeromail.core.calendar.usecases.UpdateMailboxCalendarPreferenceCommand;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-mailbox role assignments REST surface (Phase 12 W2). Routes live under {@code
 * /api/calendar/mailboxes/{mailboxId}/...} so the upstream {@code MailboxBindingFilter} binds
 * {@code MailboxContext.MAILBOX} before the controller runs — guarding mailboxes the caller does
 * not own with a 404 from the existing {@code MailboxNotOwnedException} → ProblemDetail chain.
 */
@RestController
@RequestMapping("/api/calendar/mailboxes/{mailboxId}/preferences")
@Tag(name = "calendar")
public class MailboxCalendarPreferenceController {

    private final MailboxCalendarPreferenceService mailboxCalendarPreferenceService;

    public MailboxCalendarPreferenceController(
            MailboxCalendarPreferenceService mailboxCalendarPreferenceService) {
        this.mailboxCalendarPreferenceService = mailboxCalendarPreferenceService;
    }

    @Operation(summary = "List per-role calendar assignments for a mailbox")
    @GetMapping
    public List<MailboxCalendarPreferenceResponse> list(@PathVariable UUID mailboxId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return mailboxCalendarPreferenceService.findAllForMailbox(tenantId, mailboxId).stream()
                .map(MailboxCalendarPreferenceResponse::from)
                .toList();
    }

    @Operation(summary = "Replace per-role calendar assignments for a mailbox")
    @PatchMapping
    public List<MailboxCalendarPreferenceResponse> update(
            @PathVariable UUID mailboxId,
            @Valid @RequestBody UpdateMailboxCalendarPreferenceRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        UpdateMailboxCalendarPreferenceCommand command =
                new UpdateMailboxCalendarPreferenceCommand(
                        mailboxId,
                        request.freebusyCalendarIds(),
                        request.eventWriteCalendarId(),
                        request.briefSourceCalendarId());
        return mailboxCalendarPreferenceService.updateForMailbox(tenantId, command).stream()
                .map(MailboxCalendarPreferenceResponse::from)
                .toList();
    }
}
