package com.zeromail.worker.triage;

import com.zeromail.core.inbox.domain.MessageClass;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Optional;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.util.CompatibilityHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 12 W4 (D-10) pure-function parser around ical4j 4.2.5 that extracts the minimum facts the
 * read-side pin uses: {@link MessageClass} from the calendar {@code METHOD} property and {@code
 * DTSTART} from the first {@code VEVENT}.
 *
 * <p><b>Privacy invariant (CLAUDE.md ARCH-02 / T-12-10):</b> on ANY failure path the iCal body
 * bytes are silently discarded — the parser logs at most an event marker with no body details, no
 * attendee emails, no SUMMARY text. The parse error is intentionally swallowed because its message
 * may contain a fragment of the body that triggered the failure.
 *
 * <p><b>DoS guards (T-12-04):</b>
 *
 * <ul>
 *   <li>1 MB size cap before parse — bodies over that are rejected without entering ical4j.
 *   <li>{@link CompatibilityHints#KEY_RELAXED_PARSING} and {@code KEY_RELAXED_VALIDATION} forced
 *       OFF so external-entity tricks and lenient consumption of malformed input are disabled.
 *   <li>Any exception (incl. {@link OutOfMemoryError} via {@link Throwable} catch) returns {@link
 *       Optional#empty()} — a malformed billion-laughs-style fixture must not bring down the
 *       worker.
 * </ul>
 *
 * <p><b>Per CONVENTIONS.md "Backend Code Style":</b> explicit names ({@code icsBody}, {@code
 * parseResult}, {@code classification}, {@code temporal}) — no one-letter locals.
 */
public final class CalendarPartParser {

    /** 1 MB hard cap on the iCal body before parsing (Security Domain T-12-04). */
    static final int MAX_ICS_BODY_BYTES = 1_000_000;

    private static final Logger log = LoggerFactory.getLogger(CalendarPartParser.class);

    static {
        // T-12-04: explicitly disable relaxed parsing/validation so ical4j cannot be coerced into
        // accepting malformed payloads. ical4j 4.x's defaults are already strict, but
        // CompatibilityHints is a JVM-global, so a future starter could silently flip them on —
        // pinning them off here documents and enforces the parser's posture.
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, false);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_VALIDATION, false);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, false);
    }

    /**
     * Pair extracted by a successful parse. {@code eventDt} is {@link Optional#empty()} when the
     * VEVENT carries no DTSTART (rare but legal per RFC 5545 for some VEVENT variants); the
     * classifier upstream treats an empty eventDt as "skip the projection UPDATE" because pin
     * pinning needs both columns together.
     */
    public record ParseResult(MessageClass messageClass, Optional<Instant> eventDt) {}

    /**
     * Parse a {@code text/calendar} body. Returns {@link Optional#empty()} when:
     *
     * <ul>
     *   <li>{@code icsBody} is null,
     *   <li>{@code icsBody} length exceeds {@value #MAX_ICS_BODY_BYTES} bytes,
     *   <li>ical4j throws on parse,
     *   <li>the calendar carries no recognized METHOD (only REQUEST / CANCEL / REPLY are mapped;
     *       COUNTER / REFRESH / unknown → empty).
     * </ul>
     */
    public Optional<ParseResult> parse(String icsBody) {
        if (icsBody == null || icsBody.length() > MAX_ICS_BODY_BYTES) {
            return Optional.empty();
        }
        try {
            CalendarBuilder calendarBuilder = new CalendarBuilder();
            Calendar calendar = calendarBuilder.build(new StringReader(icsBody));
            Optional<Method> methodProperty = calendar.getProperty(Property.METHOD);
            MessageClass classification =
                    classify(methodProperty.map(Method::getValue).orElse(null));
            if (classification == null) {
                return Optional.empty();
            }
            Optional<Instant> eventDt =
                    calendar.<VEvent>getComponent(Component.VEVENT)
                            .flatMap(vEvent -> vEvent.<Property>getProperty(Property.DTSTART))
                            .filter(DateProperty.class::isInstance)
                            .map(property -> ((DateProperty<?>) property).getDate())
                            .map(CalendarPartParser::toInstant);
            return Optional.of(new ParseResult(classification, eventDt));
        } catch (Throwable parseFailure) {
            // PRIVACY: never log icsBody bytes nor the exception message (may contain
            // attendee email / SUMMARY excerpt). The event marker is enough for an SRE to
            // notice a parse-failure spike without leaking content.
            log.warn("event=calendar_parse_failed_silent");
            return Optional.empty();
        }
    }

    private static MessageClass classify(String methodValue) {
        if (methodValue == null) {
            return null;
        }
        return switch (methodValue.toUpperCase()) {
            // Per Q2 lock: all METHOD:REQUEST → INVITE in v1.4 (no RESCHEDULE distinction).
            case "REQUEST" -> MessageClass.INVITE;
            case "CANCEL" -> MessageClass.CANCEL;
            case "REPLY" -> MessageClass.RSVP;
            // COUNTER / REFRESH / PUBLISH / ADD / DECLINE-COUNTER intentionally NOT mapped —
            // returning null causes the parser to return empty so the classifier writes nothing.
            default -> null;
        };
    }

    /**
     * Map the temporal types ical4j's {@link
     * net.fortuna.ical4j.model.CalendarDateFormat#DEFAULT_PARSE_FORMAT DEFAULT_PARSE_FORMAT}
     * surfaces ({@link OffsetDateTime} for {@code 20260620T130000Z}-style UTC zulu, {@link
     * LocalDateTime} for floating-time, {@link LocalDate} for VALUE=DATE) to UTC {@link Instant}.
     * {@link ZonedDateTime} appears when the property carries a TZID parameter. Naive floating
     * times and pure dates are assumed UTC because the projection is timezone-naive (display tz is
     * the user's browser).
     */
    private static Instant toInstant(Temporal temporal) {
        if (temporal == null) {
            return null;
        }
        if (temporal instanceof Instant instant) {
            return instant;
        }
        if (temporal instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (temporal instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        if (temporal instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        if (temporal instanceof LocalDate localDate) {
            return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        return null;
    }
}
