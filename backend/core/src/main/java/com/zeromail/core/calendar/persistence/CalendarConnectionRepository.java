package com.zeromail.core.calendar.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link CalendarConnectionEntity}. Always tenant-scope reads via
 * {@link #findByIdAndTenantId} or {@link #findAllByTenantId} — never {@code findById} alone, which
 * would bypass the {@code @TenantId} discriminator filter on a primary-key lookup.
 *
 * <p>Per CLAUDE.md "Backend Code Style" injection sites use the explicit name {@code
 * calendarConnectionRepository} — never {@code connectionRepo} or {@code repo}.
 */
public interface CalendarConnectionRepository
        extends JpaRepository<CalendarConnectionEntity, UUID> {

    /**
     * Tenant-scoped row lookup. Returns {@link Optional#empty()} when either the id does not exist
     * or the row's {@code tenantId} does not match the caller — both cases surface as {@code
     * CalendarConnectionNotOwnedException} at the gateway layer.
     */
    Optional<CalendarConnectionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Tenant-scoped list — used by the W2 listing endpoint and the W3 settings page; returns all
     * connections regardless of status so the UI can render disconnected rows with a "Connect
     * again" CTA.
     */
    List<CalendarConnectionEntity> findAllByTenantId(UUID tenantId);

    /**
     * Active-connection lookup by lowercased Google email. Used by the OAuth success handler to
     * detect a re-run of the same calendar grant (in which case the existing row is updated rather
     * than a {@code DataIntegrityViolationException} on the {@code uq_calendar_conn_active_email}
     * partial unique index being surfaced to the user).
     */
    @org.springframework.data.jpa.repository.Query(
            """
            SELECT c FROM CalendarConnectionEntity c
            WHERE c.tenantId = :tenantId
              AND LOWER(c.googleEmail) = LOWER(:googleEmail)
              AND c.status = com.zeromail.core.calendar.domain.CalendarConnectionStatus.CONNECTED
            """)
    Optional<CalendarConnectionEntity> findActiveByTenantIdAndGoogleEmail(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("googleEmail") String googleEmail);
}
