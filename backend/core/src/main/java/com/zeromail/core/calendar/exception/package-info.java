/**
 * Calendar business exceptions — translated by the API HTTP error pipeline (RFC 7807 ProblemDetail)
 * to dotted error codes the frontend switches on.
 *
 * <p>Members:
 *
 * <ul>
 *   <li>{@link com.zeromail.core.calendar.exception.CalendarConnectionNotOwnedException} — 404 when
 *       the {@code calendarConnectionId} does not exist for the requesting tenant.
 *   <li>{@link com.zeromail.core.calendar.exception.CalendarDisconnectedException} — 409 when the
 *       row exists but its {@code status} is not {@code CONNECTED}.
 * </ul>
 */
package com.zeromail.core.calendar.exception;
