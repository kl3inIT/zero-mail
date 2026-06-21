package com.zeromail.api.dto.calendar;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for {@code POST /api/calendar/connect-intent} (Phase 12 W3). Carries the canonical
 * Spring Security authorization URL the frontend should navigate to after the intent has been
 * stamped on Spring Session.
 *
 * <p>The URL is a hardcoded same-origin literal ({@code "/oauth2/authorization/google-calendar"}) —
 * the controller never accepts a redirect target as user input, so this surface cannot be turned
 * into an open redirect (threat T-12-09).
 */
@Schema(requiredProperties = {"authorizationUrl"})
public record CalendarConnectIntentResponse(String authorizationUrl) {}
