package com.zeromail.core.calendar.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link MailboxCalendarPreferenceEntity}. All bulk-delete paths are
 * tenant-scoped (per the T-01.2.1-07 lesson: never trust the WHERE clause alone — always carry the
 * tenantId predicate to be Modulith- and Hibernate-{@code @TenantId}-safe).
 */
public interface MailboxCalendarPreferenceRepository
        extends JpaRepository<MailboxCalendarPreferenceEntity, UUID> {

    /** All role rows attached to a given mailbox — fanned out per mailbox in the W3 settings UI. */
    List<MailboxCalendarPreferenceEntity> findAllByMailboxIdAndTenantId(
            UUID mailboxId, UUID tenantId);

    /**
     * Cascade-disconnect helper (W2): wipe every preference attached to a connection when the user
     * disconnects the workspace's Calendar grant. Tenant-scoped explicitly.
     */
    @Modifying
    @Transactional
    @Query(
            "DELETE FROM MailboxCalendarPreferenceEntity preference "
                    + "WHERE preference.calendarConnectionId = :calendarConnectionId "
                    + "AND preference.tenantId = :tenantId")
    int deleteByCalendarConnectionIdAndTenantId(
            @Param("calendarConnectionId") UUID calendarConnectionId,
            @Param("tenantId") UUID tenantId);

    /**
     * Per-sub-calendar cleanup (D-13): when the user toggles a sub-calendar off the active set, the
     * associated preference rows are removed so a re-enable does not resurrect stale role
     * assignments. Tenant-scoped explicitly.
     */
    @Modifying
    @Transactional
    @Query(
            "DELETE FROM MailboxCalendarPreferenceEntity preference "
                    + "WHERE preference.calendarId = :calendarId "
                    + "AND preference.tenantId = :tenantId")
    int deleteByCalendarIdAndTenantId(
            @Param("calendarId") UUID calendarId, @Param("tenantId") UUID tenantId);

    /**
     * Bulk delete every preference row for the given mailbox — used by {@code
     * MailboxCalendarPreferenceService.updateForMailbox} to implement replace-then-insert semantics
     * (simplest correct cardinality given the small per-mailbox N). Tenant-scoped explicitly.
     */
    @Modifying
    @Transactional
    @Query(
            "DELETE FROM MailboxCalendarPreferenceEntity preference "
                    + "WHERE preference.mailboxId = :mailboxId "
                    + "AND preference.tenantId = :tenantId")
    int deleteByMailboxIdAndTenantId(
            @Param("mailboxId") UUID mailboxId, @Param("tenantId") UUID tenantId);
}
