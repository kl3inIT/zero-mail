package com.zeromail.core.calendar.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.calendar.domain.MailboxCalendarRole;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Validates the W0 partial unique indexes on {@code mailbox_calendar_preferences} (TBD-w2-04):
 *
 * <ul>
 *   <li>FREEBUSY is multi-select per mailbox (multiple rows allowed).
 *   <li>EVENT_WRITE is single-select per mailbox — partial unique index {@code
 *       uq_mailbox_event_write} rejects the second row with PG SQLSTATE 23505.
 *   <li>BRIEF_SOURCE is single-select per mailbox — partial unique index {@code
 *       uq_mailbox_brief_source} rejects the second row with PG SQLSTATE 23505.
 *   <li>The {@code role} column accepts only {@link MailboxCalendarRole} values when written
 *       through JPA (per CONVENTIONS §4 IdentifiedEnum / {@code @Enumerated(EnumType.STRING)}
 *       contract).
 * </ul>
 */
class MailboxCalendarPreferenceConstraintTest extends PostgresContainerTest {

    @Autowired CalendarConnectionRepository calendarConnectionRepository;
    @Autowired CalendarRepository calendarRepository;
    @Autowired MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager platformTransactionManager;

    private UUID tenantId;
    private UUID mailboxId;
    private UUID calendarConnectionId;
    private UUID calendarOneId;
    private UUID calendarTwoId;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate.execute("DELETE FROM mailbox_calendar_preferences");
        jdbcTemplate.execute("DELETE FROM calendars");
        jdbcTemplate.execute("DELETE FROM calendar_connections");
        jdbcTemplate.execute("DELETE FROM gmail_connections");
        jdbcTemplate.execute("DELETE FROM tenants");

        tenantId = UUID.randomUUID();
        mailboxId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants (id, display_name, created_at) VALUES (?, ?, now())",
                tenantId,
                "constraint-test-tenant");
        // mailbox_calendar_preferences.mailbox_id has fk_mcp_mailbox → gmail_connections(id).
        // Seed a minimal gmail_connections row so the preference inserts pass the FK check.
        jdbcTemplate.update(
                "INSERT INTO gmail_connections(id, tenant_id, google_email, status, is_primary)"
                        + " VALUES (?, ?, ?, 'CONNECTED', true)",
                mailboxId,
                tenantId,
                "constraint-test-mailbox@example.test");

        TenantContext.runWith(
                tenantId,
                () -> {
                    CalendarConnectionEntity calendarConnection =
                            new CalendarConnectionEntity(
                                    UUID.randomUUID(),
                                    tenantId,
                                    "user@example.test",
                                    CalendarConnectionStatus.CONNECTED);
                    calendarConnection.setConnectedAt(Instant.now());
                    calendarConnectionId =
                            calendarConnectionRepository.save(calendarConnection).getId();

                    calendarOneId =
                            calendarRepository
                                    .save(
                                            new CalendarEntity(
                                                    UUID.randomUUID(),
                                                    tenantId,
                                                    calendarConnectionId,
                                                    "ext-cal-1",
                                                    "Calendar One",
                                                    true,
                                                    true))
                                    .getId();
                    calendarTwoId =
                            calendarRepository
                                    .save(
                                            new CalendarEntity(
                                                    UUID.randomUUID(),
                                                    tenantId,
                                                    calendarConnectionId,
                                                    "ext-cal-2",
                                                    "Calendar Two",
                                                    false,
                                                    true))
                                    .getId();
                });
    }

    @Test
    void freebusy_allows_multiple_rows_per_mailbox() {
        runAsTenant(
                () -> {
                    savePreference(MailboxCalendarRole.FREEBUSY, calendarOneId);
                    savePreference(MailboxCalendarRole.FREEBUSY, calendarTwoId);
                    assertThat(
                                    mailboxCalendarPreferenceRepository
                                            .findAllByMailboxIdAndTenantId(mailboxId, tenantId))
                            .hasSize(2);
                });
    }

    @Test
    void event_write_rejects_second_row_via_uq_mailbox_event_write() {
        runAsTenant(() -> savePreference(MailboxCalendarRole.EVENT_WRITE, calendarOneId));

        assertThatThrownBy(
                        () ->
                                runAsTenant(
                                        () ->
                                                savePreference(
                                                        MailboxCalendarRole.EVENT_WRITE,
                                                        calendarTwoId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(
                        thrown -> {
                            String message = rootMessage(thrown);
                            assertThat(message).contains("uq_mailbox_event_write");
                        });
    }

    @Test
    void brief_source_rejects_second_row_via_uq_mailbox_brief_source() {
        runAsTenant(() -> savePreference(MailboxCalendarRole.BRIEF_SOURCE, calendarOneId));

        assertThatThrownBy(
                        () ->
                                runAsTenant(
                                        () ->
                                                savePreference(
                                                        MailboxCalendarRole.BRIEF_SOURCE,
                                                        calendarTwoId)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(
                        thrown -> {
                            String message = rootMessage(thrown);
                            assertThat(message).contains("uq_mailbox_brief_source");
                        });
    }

    private void savePreference(MailboxCalendarRole role, UUID calendarId) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(platformTransactionManager);
        transactionTemplate.executeWithoutResult(
                _ ->
                        mailboxCalendarPreferenceRepository.save(
                                new MailboxCalendarPreferenceEntity(
                                        UUID.randomUUID(),
                                        tenantId,
                                        mailboxId,
                                        calendarConnectionId,
                                        calendarId,
                                        role)));
    }

    private void runAsTenant(Runnable action) {
        AtomicReference<RuntimeException> propagated = new AtomicReference<>();
        TenantContext.runWith(
                tenantId,
                () -> {
                    try {
                        action.run();
                    } catch (RuntimeException businessFailure) {
                        propagated.set(businessFailure);
                    }
                });
        if (propagated.get() != null) {
            throw propagated.get();
        }
    }

    private static String rootMessage(Throwable thrown) {
        Throwable cursor = thrown;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? "" : cursor.getMessage();
    }
}
