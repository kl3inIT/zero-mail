package com.zeromail.core.calendar.usecases;

import com.zeromail.core.calendar.domain.CalendarConnectionStatus;
import com.zeromail.core.calendar.event.CalendarConnectionDisconnected;
import com.zeromail.core.calendar.exception.CalendarConnectionNotOwnedException;
import com.zeromail.core.calendar.gateway.CalendarApiClientFactory;
import com.zeromail.core.calendar.persistence.CalendarConnectionEntity;
import com.zeromail.core.calendar.persistence.CalendarConnectionRepository;
import com.zeromail.core.calendar.persistence.CalendarEntity;
import com.zeromail.core.calendar.persistence.CalendarRepository;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceEntity;
import com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceRepository;
import com.zeromail.core.calendar.projection.CalendarConnectionView;
import com.zeromail.core.calendar.projection.CalendarConnectionView.CalendarView;
import com.zeromail.core.calendar.projection.MailboxCalendarPreferenceView;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns Calendar connection state transitions for the current workspace. Mirrors {@code
 * GmailConnectionService}'s shape — read-only list / ownership resolution use
 * {@code @Transactional(readOnly = true)}, while the multi-table cascade in {@link
 * #disconnect(UUID, UUID)} runs through a {@link TransactionTemplate} so the AFTER_COMMIT event
 * publication is paired with a single explicit transaction boundary (PATTERNS.md
 * §CalendarConnectionService line 218).
 *
 * <p><b>D-14 cascade contract:</b> {@link #disconnect(UUID, UUID)} marks the connection {@code
 * DISCONNECTED}, clears the encrypted refresh token, deletes every {@code
 * mailbox_calendar_preferences} row referencing the connection (per-mailbox role assignments cannot
 * survive the underlying grant), retains the {@code calendar_connections} row (audit retention),
 * and AFTER_COMMIT publishes {@link CalendarConnectionDisconnected}. Phase 14's {@code
 * booking_link} null-out is a no-op now per RESEARCH §Q5 (table does not exist yet).
 *
 * <p>The companion AFTER_COMMIT listener {@link
 * #onCalendarConnectionDisconnected(CalendarConnectionDisconnected)} lives in this same class
 * because the eviction target {@link CalendarApiClientFactory} is in the same Modulith module and
 * the contract is single-purpose (T-12-02 mitigation, CASA V13.1.5 — a cached access token cannot
 * outlive the grant it was minted from). Phase 13's free/busy cache listener will subscribe
 * separately in {@code core.calendar.availability} when that module ships.
 *
 * <p>Privacy logging (CONVENTIONS §5): every log line is {@code event=...} + opaque UUIDs only;
 * never {@code googleEmail}, never refresh-token bytes.
 */
@Service
public class CalendarConnectionService {

    private static final Logger log = LoggerFactory.getLogger(CalendarConnectionService.class);

    private final CalendarConnectionRepository calendarConnectionRepository;
    private final CalendarRepository calendarRepository;
    private final MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CalendarApiClientFactory calendarApiClientFactory;
    private final TransactionTemplate disconnectTransaction;

    public CalendarConnectionService(
            CalendarConnectionRepository calendarConnectionRepository,
            CalendarRepository calendarRepository,
            MailboxCalendarPreferenceRepository mailboxCalendarPreferenceRepository,
            ApplicationEventPublisher applicationEventPublisher,
            CalendarApiClientFactory calendarApiClientFactory,
            PlatformTransactionManager transactionManager) {
        this.calendarConnectionRepository = calendarConnectionRepository;
        this.calendarRepository = calendarRepository;
        this.mailboxCalendarPreferenceRepository = mailboxCalendarPreferenceRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.calendarApiClientFactory = calendarApiClientFactory;
        this.disconnectTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Read-side projection of every {@code calendar_connections} row owned by the workspace, joined
     * with each connection's sub-calendars and the per-mailbox preference rows for {@code
     * mailboxId}. Ordered by {@code connectedAt DESC} — most recently connected first.
     *
     * <p>For Phase 12 the join is small (a single workspace usually has 0-2 calendar connections
     * with 1-5 sub-calendars each); if profiling later shows N+1 issues, move the read to a Spring
     * Data JDBC native query in a dedicated read-side projection class per CONVENTIONS §2
     * CQRS-lite.
     */
    @Transactional(readOnly = true)
    public List<CalendarConnectionView> list(UUID tenantId, UUID mailboxId) {
        List<CalendarConnectionEntity> calendarConnections =
                calendarConnectionRepository.findAllByTenantId(tenantId);
        if (calendarConnections.isEmpty()) {
            return List.of();
        }
        List<MailboxCalendarPreferenceEntity> mailboxPreferenceRows =
                mailboxCalendarPreferenceRepository.findAllByMailboxIdAndTenantId(
                        mailboxId, tenantId);
        Map<UUID, List<MailboxCalendarPreferenceView>> preferencesByConnectionId =
                mailboxPreferenceRows.stream()
                        .map(
                                preferenceRow ->
                                        new MailboxCalendarPreferenceView(
                                                preferenceRow.getId(),
                                                preferenceRow.getMailboxId(),
                                                preferenceRow.getCalendarConnectionId(),
                                                preferenceRow.getCalendarId(),
                                                preferenceRow.getRole()))
                        .collect(
                                Collectors.groupingBy(
                                        MailboxCalendarPreferenceView::calendarConnectionId));
        return calendarConnections.stream()
                .sorted(
                        Comparator.comparing(
                                        CalendarConnectionEntity::getConnectedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(CalendarConnectionEntity::getId))
                .map(
                        calendarConnection -> {
                            List<CalendarEntity> subCalendars =
                                    calendarRepository.findAllByCalendarConnectionIdAndTenantId(
                                            calendarConnection.getId(), tenantId);
                            List<CalendarView> calendarViews =
                                    subCalendars.stream()
                                            .map(
                                                    calendar ->
                                                            new CalendarView(
                                                                    calendar.getId(),
                                                                    calendar
                                                                            .getExternalCalendarId(),
                                                                    calendar.getName(),
                                                                    calendar.isPrimary(),
                                                                    calendar.isEnabled(),
                                                                    calendar.getTimezone()))
                                            .toList();
                            List<MailboxCalendarPreferenceView> preferenceViews =
                                    preferencesByConnectionId.getOrDefault(
                                            calendarConnection.getId(), List.of());
                            return new CalendarConnectionView(
                                    calendarConnection.getId(),
                                    calendarConnection.getTenantId(),
                                    calendarConnection.getGoogleEmail(),
                                    calendarConnection.getStatus(),
                                    calendarConnection.getConnectedAt(),
                                    calendarConnection.getDisconnectedAt(),
                                    calendarConnection.getGoogleProfileName(),
                                    calendarConnection.getGoogleProfilePictureUrl(),
                                    calendarViews,
                                    preferenceViews);
                        })
                .toList();
    }

    /**
     * Tenant-scoped ownership resolution. Throws {@link CalendarConnectionNotOwnedException} (HTTP
     * 404 via the central {@code GlobalExceptionHandler}) when the row does not exist OR the
     * caller's tenant does not own it — both cases collapse into the same 404 to avoid leaking
     * existence.
     */
    @Transactional(readOnly = true)
    public CalendarConnectionEntity resolveOwnedConnectionOrThrow(
            UUID tenantId, UUID calendarConnectionId) {
        return calendarConnectionRepository
                .findByIdAndTenantId(calendarConnectionId, tenantId)
                .orElseThrow(
                        () -> {
                            log.warn(
                                    "event=calendar_connection_resolve_denied tenantId={} calendarConnectionId={} reason=not_owned",
                                    tenantId,
                                    calendarConnectionId);
                            return new CalendarConnectionNotOwnedException(
                                    tenantId, calendarConnectionId);
                        });
    }

    /**
     * D-14 cascade-disconnect (synchronous, multi-table). Idempotent — calling disconnect on an
     * already-DISCONNECTED row is a no-op (no second event published).
     *
     * <p>Inside the transaction:
     *
     * <ol>
     *   <li>Re-resolve the row under the tx (so a concurrent disconnect that committed before this
     *       tx started is observed correctly).
     *   <li>Short-circuit if {@code status != CONNECTED} — idempotent path.
     *   <li>Mark {@code status=DISCONNECTED}, set {@code disconnectedAt=now()}, and clear the
     *       encrypted refresh token (per CASA V13.1.5 — the local row must not retain a usable
     *       grant after disconnect).
     *   <li>Bulk-delete every {@code mailbox_calendar_preferences} row referencing this connection
     *       (tenant-scoped — the repository method explicitly carries the tenantId predicate per
     *       T-01.2.1-07 lesson).
     *   <li>Save the modified connection row.
     *   <li>Phase 14's {@code booking_link.destination_calendar_id} null-out is deferred — the
     *       table does not exist in v1.4 (RESEARCH §Q5).
     * </ol>
     *
     * <p>After commit (Spring publishes the event only when the tx commits — a rollback leaves no
     * event in flight), the {@link CalendarConnectionDisconnected} event is published. The
     * companion AFTER_COMMIT listener evicts the {@link CalendarApiClientFactory} access-token
     * cache; Phase 13's free/busy cache listener will subscribe to the same event when it ships.
     */
    public void disconnect(UUID tenantId, UUID calendarConnectionId) {
        boolean stateChanged =
                Boolean.TRUE.equals(
                        disconnectTransaction.execute(
                                _ -> {
                                    CalendarConnectionEntity calendarConnection =
                                            resolveOwnedConnectionOrThrow(
                                                    tenantId, calendarConnectionId);
                                    if (calendarConnection.getStatus()
                                            != CalendarConnectionStatus.CONNECTED) {
                                        return Boolean.FALSE;
                                    }
                                    calendarConnection.setStatus(
                                            CalendarConnectionStatus.DISCONNECTED);
                                    calendarConnection.setDisconnectedAt(Instant.now());
                                    calendarConnection.setRefreshTokenEncrypted(null);
                                    int deletedPreferenceRows =
                                            mailboxCalendarPreferenceRepository
                                                    .deleteByCalendarConnectionIdAndTenantId(
                                                            calendarConnectionId, tenantId);
                                    calendarConnectionRepository.save(calendarConnection);
                                    log.info(
                                            "event=calendar_connection_disconnected tenantId={} calendarConnectionId={} preferenceRowsDeleted={}",
                                            tenantId,
                                            calendarConnectionId,
                                            deletedPreferenceRows);
                                    return Boolean.TRUE;
                                }));
        if (stateChanged) {
            applicationEventPublisher.publishEvent(
                    new CalendarConnectionDisconnected(
                            tenantId, calendarConnectionId, Instant.now()));
        }
    }

    /**
     * AFTER_COMMIT listener for {@link CalendarConnectionDisconnected}. Evicts the {@link
     * CalendarApiClientFactory} access-token cache so a still-valid cached token (~59 min TTL)
     * cannot keep issuing Calendar API calls against a connection the user just disconnected
     * (T-12-02 mitigation, CASA V13.1.5). NO repository writes happen here — the listener is
     * read-only on application state.
     *
     * <p>Per CONVENTIONS §6, in-process AFTER_COMMIT side effects are the canonical Modulith event
     * use case. Phase 13's free/busy cache listener subscribes via the same event when it ships,
     * keeping the disconnect contract single-source.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCalendarConnectionDisconnected(CalendarConnectionDisconnected event) {
        calendarApiClientFactory.evictAccessToken(event.calendarConnectionId());
        log.info(
                "event=calendar_access_token_cache_evicted reason=connection_disconnected tenantId={} calendarConnectionId={}",
                event.tenantId(),
                event.calendarConnectionId());
    }
}
