package com.zeromail.api.controllers.calendar;

import com.zeromail.api.dto.calendar.CalendarConnectionResponse;
import com.zeromail.api.dto.calendar.CalendarToggleResponse;
import com.zeromail.api.dto.calendar.UpdateCalendarEnabledRequest;
import com.zeromail.core.calendar.usecases.CalendarConnectionService;
import com.zeromail.core.calendar.usecases.CalendarToggleService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the workspace's Calendar connections (Phase 12 W2). Thin per CONVENTIONS §1 —
 * injects only services, never repositories; response mapping is delegated to record {@code from}
 * factories.
 *
 * <p>Error translation is centralized in {@code GlobalExceptionHandler}: {@code
 * CalendarConnectionNotOwnedException} → 404, {@code CalendarDisconnectedException} → 409, {@code
 * IllegalArgumentException} → 400 (from service-layer ownership/enabled validation).
 */
@RestController
@RequestMapping("/api/calendar")
@Tag(name = "calendar")
public class CalendarConnectionController {

    private final CalendarConnectionService calendarConnectionService;
    private final CalendarToggleService calendarToggleService;

    public CalendarConnectionController(
            CalendarConnectionService calendarConnectionService,
            CalendarToggleService calendarToggleService) {
        this.calendarConnectionService = calendarConnectionService;
        this.calendarToggleService = calendarToggleService;
    }

    @Operation(summary = "List Calendar connections for the given mailbox")
    @GetMapping("/mailboxes/{mailboxId}/connections")
    public List<CalendarConnectionResponse> listConnectionsForMailbox(
            @PathVariable UUID mailboxId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return calendarConnectionService.list(tenantId, mailboxId).stream()
                .map(CalendarConnectionResponse::from)
                .toList();
    }

    @Operation(summary = "Disconnect a Calendar connection")
    @DeleteMapping("/connections/{calendarConnectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable UUID calendarConnectionId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        calendarConnectionService.disconnect(tenantId, calendarConnectionId);
    }

    @Operation(summary = "Toggle a sub-calendar's is_enabled flag")
    @PatchMapping("/calendars/{calendarId}/enabled")
    public CalendarToggleResponse toggleEnabled(
            @PathVariable UUID calendarId, @RequestBody UpdateCalendarEnabledRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        int preferencesRemoved =
                calendarToggleService.setEnabled(tenantId, calendarId, request.enabled());
        return new CalendarToggleResponse(preferencesRemoved);
    }
}
