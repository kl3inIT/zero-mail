package com.zeromail.api.dto.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Body for {@code POST /api/calendar/connect-intent} (Phase 12 W3, D-06). Carries the active {@code
 * mailboxId} the user is connecting Google Calendar from, so {@code CalendarOAuthSuccessHandler}
 * can seed default role rows for that mailbox only after the OAuth round-trip.
 *
 * <p>The mailbox ID is validated for tenant ownership in the controller (via {@code
 * GmailConnectionService.resolveOwnedConnectionOrThrow}) before the intent is stamped on Spring
 * Session, so an attacker cannot stamp an arbitrary {@code targetMailboxId} on their own session.
 */
@Schema(requiredProperties = {"mailboxId"})
public record CalendarConnectIntentRequest(@NotNull UUID mailboxId) {}
