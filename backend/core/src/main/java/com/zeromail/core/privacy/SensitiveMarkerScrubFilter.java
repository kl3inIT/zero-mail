package com.zeromail.core.privacy;

import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Defense-in-depth catch for stray {@code Sensitive(...)} tokens that bypassed
 * {@link Sensitive#toString()} (e.g. a custom formatter that printed the underlying record
 * components, or a literal string). When detected, stamps {@code scrubbed=true} and
 * {@code scrub_reason=sensitive_marker} on MDC so the JSON encoder includes those keys on the
 * emitted event, giving SOC alerting a structured signal.
 *
 * Why MDC-only and not message rewrite: Logback dispatches single-arg log calls
 * ({@code logger.info(format, arg)}) through a TurboFilter using a transient one-shot
 * argument array; mutating that array does not propagate to the LoggingEvent that the
 * encoder later renders. The primary redaction contract is therefore delivered by
 * {@link Sensitive#toString()} returning {@code ***REDACTED***}; this filter is the
 * observable signal that something slipped past that contract.
 *
 * Does NOT re-log from within the filter (avoids TurboFilter recursion); returns NEUTRAL.
 */
public class SensitiveMarkerScrubFilter extends TurboFilter {

    private static final String TOKEN = "Sensitive(";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        if (format == null) {
            return FilterReply.NEUTRAL;
        }
        String rendered = MessageFormatter.arrayFormat(format, params).getMessage();
        if (rendered == null || !rendered.contains(TOKEN)) {
            return FilterReply.NEUTRAL;
        }
        MDC.put("scrubbed", "true");
        MDC.put("scrub_reason", "sensitive_marker");
        return FilterReply.NEUTRAL;
    }
}
