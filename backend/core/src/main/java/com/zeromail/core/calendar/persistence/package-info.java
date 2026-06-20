/**
 * Calendar JPA persistence: entities + Spring Data repositories for the four W0 schema tables
 * ({@code calendar_connections}, {@code calendars}, {@code mailbox_calendar_preferences}; W4 adds
 * read paths over {@code gmail_inbox_projection}).
 *
 * <p>All entities extend {@code AbstractTenantOwnedEntity} for the Hibernate 7 {@code @TenantId}
 * filter (multi-tenant row-level isolation, FND-05). The {@code refresh_token_encrypted} column on
 * {@code CalendarConnectionEntity} is written ONLY through the {@code OAuthTokenStore} facade with
 * {@code RowDiscriminator.CALENDAR_CONNECTION} — never via the raw {@code RefreshTokenCipher} call
 * (T-12-V6 mitigation).
 */
package com.zeromail.core.calendar.persistence;
