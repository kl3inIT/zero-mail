package com.zeromail.core.calendar.exception;

import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.UUID;

/**
 * Thrown when a tenant's {@code calendar_connection} row exists but is not in {@link
 * CalendarConnectionStatus#CONNECTED} state — the only status under which {@code
 * CalendarApiClientFactory} mints a Google Calendar client. Surfaces as HTTP 409 via the {@code
 * ErrorClass.CONFLICT} mapping.
 *
 * <p>The carried {@link #status()} lets the API translate REVOKED → "re-grant required" copy and
 * DISCONNECTED → "connect again" copy at the frontend.
 *
 * <p>Privacy: the message intentionally carries only opaque UUIDs + the status enum — never {@code
 * google_email}.
 */
public final class CalendarDisconnectedException extends BusinessException {

    private final UUID tenantId;
    private final UUID calendarConnectionId;
    private final CalendarConnectionStatus status;

    public CalendarDisconnectedException(
            UUID tenantId, UUID calendarConnectionId, CalendarConnectionStatus status) {
        super(
                "calendar_connection_disconnected tenantId="
                        + tenantId
                        + " calendarConnectionId="
                        + calendarConnectionId
                        + " status="
                        + status);
        this.tenantId = tenantId;
        this.calendarConnectionId = calendarConnectionId;
        this.status = status;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID calendarConnectionId() {
        return calendarConnectionId;
    }

    public CalendarConnectionStatus status() {
        return status;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.CALENDAR_DISCONNECTED;
    }

    @Override
    public String logEvent() {
        return "calendar_connection_disconnected";
    }

    @Override
    public String title() {
        return "Calendar connection is not active";
    }

    @Override
    public String detail() {
        return "The selected calendar connection is not currently connected.";
    }
}
