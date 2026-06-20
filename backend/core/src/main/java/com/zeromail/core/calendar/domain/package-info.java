/**
 * Calendar domain enums (tactical-DDD value objects). Framework-free per {@code
 * DomainPurityArchTest}: no Spring / Jackson / Jakarta / Hibernate imports allowed in this package.
 *
 * <p>Members:
 *
 * <ul>
 *   <li>{@link com.zeromail.core.calendar.domain.CalendarConnectionStatus} — {@code IdentifiedEnum}
 *       three-state machine (CONNECTED / DISCONNECTED / REVOKED) per CAL-CONN-08.
 *   <li>{@link com.zeromail.core.calendar.domain.MailboxCalendarRole} — {@code IdentifiedEnum}
 *       per-mailbox role (FREEBUSY / EVENT_WRITE / BRIEF_SOURCE) per CAL-CONN-07 + Q3 lock.
 * </ul>
 */
package com.zeromail.core.calendar.domain;
