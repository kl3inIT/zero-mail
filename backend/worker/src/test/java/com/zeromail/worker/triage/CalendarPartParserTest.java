package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.inbox.domain.MessageClass;
import com.zeromail.worker.triage.CalendarPartParser.ParseResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Phase 12 W4 (Task 2) unit-level coverage of the ical4j parser wrapper. Tests run as plain JUnit 5
 * — no Spring context — because the parser is a pure function with no collaborators (Layer 1 per
 * TESTING.md §3).
 *
 * <p>Coverage matrix:
 *
 * <ul>
 *   <li>RFC 5546 METHOD → {@link MessageClass} mapping (REQUEST/CANCEL/REPLY + COUNTER not mapped)
 *   <li>{@code DTSTART} extraction in UTC + folded-line resilience (RFC 5545 §3.1)
 *   <li>Q2 lock: {@code METHOD:REQUEST} NEVER classified as RESCHEDULE in v1.4
 *   <li>T-12-04 DoS: oversized body (&gt;1 MB) → empty, no OOM, completes in &lt;2 s
 *   <li>T-12-10 privacy: parser never logs the iCal body or its SUMMARY/ATTENDEE values
 *   <li>Null/empty/truncated/unknown-METHOD edge cases all return {@link Optional#empty()}
 * </ul>
 */
class CalendarPartParserTest {

    private final CalendarPartParser parser = new CalendarPartParser();

    @Test
    void parse_invite_returnsInvite() {
        Optional<ParseResult> parseResult = parser.parse(loadFixture("invite-request"));
        assertThat(parseResult).isPresent();
        ParseResult result = parseResult.orElseThrow();
        assertThat(result.messageClass()).isEqualTo(MessageClass.INVITE);
        assertThat(result.eventDt()).contains(Instant.parse("2026-06-20T13:00:00Z"));
    }

    @Test
    void parse_cancel_returnsCancel() {
        Optional<ParseResult> parseResult = parser.parse(loadFixture("cancel"));
        assertThat(parseResult).isPresent();
        assertThat(parseResult.orElseThrow().messageClass()).isEqualTo(MessageClass.CANCEL);
    }

    @Test
    void parse_reply_returnsRsvp() {
        Optional<ParseResult> parseResult = parser.parse(loadFixture("reply"));
        assertThat(parseResult).isPresent();
        assertThat(parseResult.orElseThrow().messageClass()).isEqualTo(MessageClass.RSVP);
    }

    @Test
    void parse_methodRequest_doesNotEmitReschedule() {
        // Q2 explicit lock — METHOD:REQUEST never maps to RESCHEDULE in v1.4 even for repeated
        // invites against the same UID. Distinguishing initial vs reschedule requires a UID
        // lookup that lives in a follow-up phase.
        Optional<ParseResult> parseResult = parser.parse(loadFixture("invite-request"));
        assertThat(parseResult).isPresent();
        assertThat(parseResult.orElseThrow().messageClass()).isNotEqualTo(MessageClass.RESCHEDULE);
    }

    @Test
    void parse_foldedLines_doesNotCorruptDtstart() {
        // RFC 5545 §3.1 line folding (continuation lines begin with SPACE/HTAB). A naive regex
        // parser would mis-read the folded SUMMARY/DESCRIPTION and could land DTSTART inside a
        // continuation segment; ical4j unfolds correctly.
        Optional<ParseResult> parseResult = parser.parse(loadFixture("folded-lines"));
        assertThat(parseResult).isPresent();
        ParseResult result = parseResult.orElseThrow();
        assertThat(result.messageClass()).isEqualTo(MessageClass.INVITE);
        assertThat(result.eventDt()).contains(Instant.parse("2026-06-20T13:00:00Z"));
    }

    @Test
    void parse_billionLaughs_returnsEmpty() {
        // T-12-04 DoS test. Fixture is >1 MB so the size-bound preflight short-circuits before
        // entering ical4j. Ensures no OOM, no >2 s parse latency.
        long startedAtNanos = System.nanoTime();
        Optional<ParseResult> parseResult = parser.parse(loadFixture("billion-laughs"));
        long elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        assertThat(parseResult).isEmpty();
        assertThat(elapsedMillis)
                .as("size-bound preflight must reject in <2s without entering ical4j")
                .isLessThan(2_000);
    }

    @Test
    void parse_null_returnsEmpty() {
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    void parse_emptyString_returnsEmpty() {
        assertThat(parser.parse("")).isEmpty();
    }

    @Test
    void parse_truncatedIcs_returnsEmpty() {
        // Missing END:VCALENDAR — ical4j throws; parser silent-fails to Optional.empty.
        assertThat(parser.parse("BEGIN:VCALENDAR\r\nMETHOD:REQUEST\r\n")).isEmpty();
    }

    @Test
    void parse_methodCounter_returnsEmpty() {
        String counterIcs =
                """
                BEGIN:VCALENDAR
                PRODID:-//Zero Mail Test Fixture//EN
                VERSION:2.0
                METHOD:COUNTER
                BEGIN:VEVENT
                UID:test-counter@zeromail.test
                DTSTAMP:20260620T120000Z
                DTSTART:20260620T140000Z
                SUMMARY:Counter proposal
                ORGANIZER:mailto:organizer@example.com
                ATTENDEE:mailto:attendee@example.com
                END:VEVENT
                END:VCALENDAR
                """;
        assertThat(parser.parse(counterIcs)).isEmpty();
    }

    @Test
    void parse_neverLogsIcsBody() {
        // T-12-10 privacy invariant: on failure the parser may log a marker event, but the iCal
        // body bytes (incl. SUMMARY and ATTENDEE values) MUST NOT appear in the log line.
        Logger parserLogger = (Logger) LoggerFactory.getLogger(CalendarPartParser.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        parserLogger.addAppender(appender);
        Level previousLevel = parserLogger.getLevel();
        parserLogger.setLevel(Level.ALL);
        try {
            // Truncated body containing PII-shaped tokens — force the catch-all silent log path.
            String piiBody =
                    "BEGIN:VCALENDAR\r\n"
                            + "METHOD:REQUEST\r\n"
                            + "SUMMARY:CONFIDENTIAL-SUBJECT-DO-NOT-LOG\r\n"
                            + "ATTENDEE:mailto:secret-attendee@example.com\r\n";
            parser.parse(piiBody);
        } finally {
            parserLogger.detachAppender(appender);
            parserLogger.setLevel(previousLevel);
            appender.stop();
        }

        // Every log line must be free of the body's PII fragments.
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage())
                    .doesNotContain("CONFIDENTIAL-SUBJECT-DO-NOT-LOG")
                    .doesNotContain("secret-attendee@example.com")
                    .doesNotContain("BEGIN:VCALENDAR");
        }
    }

    private String loadFixture(String name) {
        String resourcePath = "/ical/" + name + ".ics";
        try (InputStream stream = CalendarPartParserTest.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Fixture not on classpath: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed reading fixture " + resourcePath, ioException);
        }
    }
}
