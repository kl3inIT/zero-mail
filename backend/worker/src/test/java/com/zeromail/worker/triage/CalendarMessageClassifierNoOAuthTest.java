package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.zeromail.core.calendar.gateway.CalendarApiClientFactory;
import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.inbox.domain.InboxState;
import com.zeromail.core.inbox.domain.MessageClass;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionEntity;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionId;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.worker.PostgresContainerTest;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 12 W4 (Task 3) end-to-end gate for the CAL-TRIAGE-04 invariant: {@link
 * CalendarMessageClassifier} MUST classify {@code text/calendar} messages WITHOUT ever touching
 * {@link CalendarApiClientFactory}. Every Zero Mail user sees pinned invites regardless of whether
 * they connect their Google Calendar — that is what makes calendar-aware triage work before a
 * Calendar OAuth grant exists.
 *
 * <p>The test wires a real Spring context (the classifier's @TransactionalEventListener fires only
 * when a real transaction publishes the event), mocks {@link GmailApiClientFactory} so a
 * deterministic {@code text/calendar} body is returned, and {@link MockitoBean}-spies {@link
 * CalendarApiClientFactory} so any accidental invocation surfaces via {@code verify(..., never())}.
 */
class CalendarMessageClassifierNoOAuthTest extends PostgresContainerTest {

    private static final UUID GMAIL_CONNECTION_ID =
            UUID.fromString("00000000-0000-0000-0000-00000000ccff");

    private static final String INVITE_ICS =
            """
            BEGIN:VCALENDAR
            PRODID:-//Zero Mail Test Fixture//EN
            VERSION:2.0
            METHOD:REQUEST
            BEGIN:VEVENT
            UID:test-invite-1@zeromail.test
            DTSTAMP:20260620T120000Z
            DTSTART:20260620T130000Z
            DTEND:20260620T140000Z
            SUMMARY:Test Meeting
            ORGANIZER:mailto:organizer@example.com
            ATTENDEE:mailto:attendee@example.com
            END:VEVENT
            END:VCALENDAR
            """;

    @MockitoBean GmailApiClientFactory gmailApiClientFactory;
    @MockitoBean CalendarApiClientFactory calendarApiClientFactory;

    @Autowired ApplicationEventPublisher applicationEventPublisher;
    @Autowired GmailInboxProjectionRepository gmailInboxProjectionRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void classifies_invite_without_invoking_calendar_oauth() throws Exception {
        UUID tenantId = seedTenantWithProjection();
        String gmailMessageId = "gmail-msg-invite-1";
        seedProjectionRow(tenantId, gmailMessageId);
        wireGmailApiToReturnCalendarMessage(INVITE_ICS);

        publishInTx(
                new MailMessageObserved(
                        tenantId, GMAIL_CONNECTION_ID, gmailMessageId, "thread-1", Instant.now()));

        GmailInboxProjectionEntity reloaded =
                loadProjection(tenantId, gmailMessageId).orElseThrow();
        assertThat(reloaded.getMessageClassOptional()).contains(MessageClass.INVITE);
        assertThat(reloaded.getEventDtOptional()).contains(Instant.parse("2026-06-20T13:00:00Z"));

        // CAL-TRIAGE-04 invariant: the calendar gateway is NEVER touched by the classifier path.
        verify(calendarApiClientFactory, never()).buildClientForCalendarConnection(any(), any());
    }

    @Test
    void leaves_projection_untouched_when_message_is_not_calendar() throws Exception {
        UUID tenantId = seedTenantWithProjection();
        String gmailMessageId = "gmail-msg-plain-1";
        seedProjectionRow(tenantId, gmailMessageId);
        wireGmailApiToReturnPlainTextMessage();

        publishInTx(
                new MailMessageObserved(
                        tenantId,
                        GMAIL_CONNECTION_ID,
                        gmailMessageId,
                        "thread-plain",
                        Instant.now()));

        GmailInboxProjectionEntity reloaded =
                loadProjection(tenantId, gmailMessageId).orElseThrow();
        assertThat(reloaded.getMessageClassOptional()).isEmpty();
        assertThat(reloaded.getEventDtOptional()).isEmpty();

        verify(calendarApiClientFactory, never()).buildClientForCalendarConnection(any(), any());
    }

    @Test
    void skips_update_when_parser_silent_fails() throws Exception {
        UUID tenantId = seedTenantWithProjection();
        String gmailMessageId = "gmail-msg-bad-ics";
        seedProjectionRow(tenantId, gmailMessageId);
        // text/calendar body whose METHOD line is missing — parser returns Optional.empty.
        wireGmailApiToReturnCalendarMessage("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n");

        publishInTx(
                new MailMessageObserved(
                        tenantId,
                        GMAIL_CONNECTION_ID,
                        gmailMessageId,
                        "thread-bad-ics",
                        Instant.now()));

        GmailInboxProjectionEntity reloaded =
                loadProjection(tenantId, gmailMessageId).orElseThrow();
        assertThat(reloaded.getMessageClassOptional()).isEmpty();
        assertThat(reloaded.getEventDtOptional()).isEmpty();

        verify(calendarApiClientFactory, never()).buildClientForCalendarConnection(any(), any());
    }

    private void wireGmailApiToReturnCalendarMessage(String icsBody) throws IOException {
        // Build the Gmail Message stub: one multipart payload with a text/calendar child carrying
        // the base64-url-encoded iCal body.
        MessagePartBody calendarBody = new MessagePartBody();
        calendarBody.setData(
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(icsBody.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        MessagePart calendarPart = new MessagePart();
        calendarPart.setMimeType("text/calendar");
        calendarPart.setBody(calendarBody);

        MessagePart payload = new MessagePart();
        payload.setMimeType("multipart/mixed");
        payload.setParts(List.of(calendarPart));

        Message message = new Message();
        message.setPayload(payload);

        stubGmailUsersMessagesGet(message);
    }

    private void wireGmailApiToReturnPlainTextMessage() throws IOException {
        MessagePartBody textBody = new MessagePartBody();
        textBody.setData(
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                "Hello, this is a regular email"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        MessagePart textPart = new MessagePart();
        textPart.setMimeType("text/plain");
        textPart.setBody(textBody);

        Message message = new Message();
        message.setPayload(textPart);
        stubGmailUsersMessagesGet(message);
    }

    private void stubGmailUsersMessagesGet(Message message) throws IOException {
        Gmail gmailClient =
                org.mockito.Mockito.mock(Gmail.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(gmailClient
                        .users()
                        .messages()
                        .get(
                                org.mockito.ArgumentMatchers.eq("me"),
                                org.mockito.ArgumentMatchers.anyString())
                        .setFormat(org.mockito.ArgumentMatchers.anyString())
                        .execute())
                .thenReturn(message);
        when(gmailApiClientFactory.buildClientForMailbox(any())).thenReturn(gmailClient);
    }

    private void publishInTx(MailMessageObserved event) {
        // Publish inside a freshly-opened TX so AFTER_COMMIT actually fires (vs. the surrounding
        // @Transactional which only commits at the test method's end).
        transactionTemplate.executeWithoutResult(
                _ ->
                        TenantContext.runWith(
                                event.tenantId(),
                                () -> applicationEventPublisher.publishEvent(event)));
    }

    private Optional<GmailInboxProjectionEntity> loadProjection(
            UUID tenantId, String gmailMessageId) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                gmailInboxProjectionRepository.findById(
                                        new GmailInboxProjectionId(
                                                tenantId, GMAIL_CONNECTION_ID, gmailMessageId)));
    }

    private UUID seedTenantWithProjection() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        return tenantId;
    }

    private void seedProjectionRow(UUID tenantId, String gmailMessageId) {
        Instant now = Instant.now();
        TenantContext.runWith(
                tenantId,
                () ->
                        gmailInboxProjectionRepository.upsertProjection(
                                tenantId,
                                GMAIL_CONNECTION_ID,
                                gmailMessageId,
                                "thread-" + gmailMessageId,
                                new byte[32],
                                new byte[64],
                                null,
                                null,
                                null,
                                false,
                                now.minus(Duration.ofMinutes(5)),
                                new String[] {"INBOX"},
                                InboxState.INBOX.id(),
                                false,
                                100L,
                                now,
                                now.plus(Duration.ofDays(90))));
    }
}
