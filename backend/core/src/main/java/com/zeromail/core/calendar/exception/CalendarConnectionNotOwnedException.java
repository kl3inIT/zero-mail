package com.zeromail.core.calendar.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.UUID;

/**
 * Thrown when a tenant attempts to read or operate on a {@code calendar_connection} row it does not
 * own (either the row does not exist, or the {@code tenant_id} column does not match the caller's
 * {@code TenantContext}). Surfaces as HTTP 404 via the {@code ErrorClass.NOT_FOUND} mapping.
 *
 * <p>Privacy: the message intentionally carries only opaque UUIDs — never {@code google_email}.
 */
public final class CalendarConnectionNotOwnedException extends BusinessException {

    private final UUID tenantId;
    private final UUID calendarConnectionId;

    public CalendarConnectionNotOwnedException(UUID tenantId, UUID calendarConnectionId) {
        super(
                "calendar_connection_not_owned tenantId="
                        + tenantId
                        + " calendarConnectionId="
                        + calendarConnectionId);
        this.tenantId = tenantId;
        this.calendarConnectionId = calendarConnectionId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID calendarConnectionId() {
        return calendarConnectionId;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.NOT_FOUND;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.CALENDAR_CONNECTION_NOT_FOUND;
    }

    @Override
    public String logEvent() {
        return "calendar_connection_not_owned";
    }

    @Override
    public String title() {
        return "Calendar connection not found";
    }

    @Override
    public String detail() {
        return "The selected calendar connection does not exist in this workspace.";
    }
}
