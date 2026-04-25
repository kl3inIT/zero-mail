package com.zeromail.core.privacy;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Defense-in-depth catch for stray {@code Sensitive(...)} tokens that bypassed
 * {@link Sensitive#toString()} (e.g. a custom formatter that printed the underlying record
 * components, or a literal string).
 *
 * <p><b>Per-event, NOT MDC-sticky.</b> The previous TurboFilter implementation wrote
 * {@code scrubbed=true} into thread-local MDC, which then bled into subsequent (clean) log
 * events on the same execution path — producing false audit signals. This implementation
 * is registered as an appender-level {@link Filter} over already-built {@link ILoggingEvent}
 * instances, so the {@code scrubbed} marker is attached only to the offending event's own
 * MDC snapshot and never persists into thread state.
 *
 * <p>The primary redaction contract is still delivered by {@link Sensitive#toString()}
 * returning {@code ***REDACTED***}; this filter is the structured signal that something
 * slipped past that contract so SOC alerting can fire on it.
 *
 * <p>This filter never blocks events — it always returns {@link FilterReply#NEUTRAL}.
 */
public class SensitiveMarkerScrubFilter extends Filter<ILoggingEvent> {

    private static final String TOKEN = "Sensitive(";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event == null) {
            return FilterReply.NEUTRAL;
        }
        String message = event.getFormattedMessage();
        if (message == null || !message.contains(TOKEN)) {
            return FilterReply.NEUTRAL;
        }
        stampScrubbed(event);
        return FilterReply.NEUTRAL;
    }

    private static void stampScrubbed(ILoggingEvent event) {
        // We copy-on-write: build a new map containing the existing MDC snapshot plus the
        // scrubbed/scrub_reason markers, then install it as the event's mdcPropertyMap.
        // Logback's public LoggingEvent#setMDCPropertyMap rejects re-assignment after the
        // event captured MDC, so we set the field reflectively. This mutation applies ONLY
        // to this event and never touches thread-local MDC, so subsequent events stay clean.
        if (!(event instanceof LoggingEvent classic)) {
            return;
        }
        Map<String, String> existing = classic.getMDCPropertyMap();
        Map<String, String> copy = existing == null ? new HashMap<>() : new HashMap<>(existing);
        copy.put("scrubbed", "true");
        copy.put("scrub_reason", "sensitive_marker");
        try {
            Field f = LoggingEvent.class.getDeclaredField("mdcPropertyMap");
            f.setAccessible(true);
            f.set(classic, copy);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // If the Logback layout ever changes the field name we want to know about it
            // loudly during tests/CI, but never break a production log emission for it.
            throw new IllegalStateException(
                    "Logback LoggingEvent layout changed; SensitiveMarkerScrubFilter needs update", e);
        }
    }
}
