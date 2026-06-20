/**
 * Calendar outbound gateway: builds Google Calendar API clients ({@code
 * com.google.api.services.calendar.Calendar}) for a given {@code (tenantId, calendarConnectionId)}
 * pair.
 *
 * <p>{@link com.zeromail.core.calendar.gateway.CalendarApiClientFactory} mirrors {@code
 * GmailApiClientFactory} — per-connection access-token cache, refresh-token decrypt via {@code
 * OAuthTokenStore}, fail-fast on non-CONNECTED status with {@code CalendarDisconnectedException}.
 * It shares the {@code google} OAuth client-id/secret with Gmail (single GCP OAuth client per the
 * Phase 12 Pitfall 2 mitigation).
 */
package com.zeromail.core.calendar.gateway;
