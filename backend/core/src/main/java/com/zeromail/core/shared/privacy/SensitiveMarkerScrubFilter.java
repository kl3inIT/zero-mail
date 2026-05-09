package com.zeromail.core.shared.privacy;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Defense-in-depth catch for stray {@code Sensitive(...)} tokens that bypassed {@link
 * Sensitive#toString()} (e.g. a custom formatter that printed the underlying record components, or
 * a literal string).
 *
 * <p><b>Per-event, NOT MDC-sticky.</b> The previous TurboFilter implementation wrote {@code
 * scrubbed=true} into thread-local MDC, which then bled into subsequent (clean) log events on the
 * same execution path — producing false audit signals. This implementation is registered as an
 * appender-level {@link Filter} over already-built {@link ILoggingEvent} instances, so the {@code
 * scrubbed} marker is attached only to the offending event's own MDC snapshot and never persists
 * into thread state.
 *
 * <p>The primary redaction contract is still delivered by {@link Sensitive#toString()} returning
 * {@code ***REDACTED***}; this filter is the structured signal that something slipped past that
 * contract so SOC alerting can fire on it.
 *
 * <p>This filter never blocks events — it always returns {@link FilterReply#NEUTRAL}.
 */
public class SensitiveMarkerScrubFilter extends Filter<ILoggingEvent> {

  private static final String TOKEN = "Sensitive(";
  private static final List<ScrubPattern> SECRET_PATTERNS =
      List.of(
          new ScrubPattern(Pattern.compile("apiKey=([^\\s,;]+)"), "apiKey=***REDACTED***"),
          new ScrubPattern(
              Pattern.compile("Bearer\\s+([A-Za-z0-9_\\-.]+)"), "Bearer ***REDACTED***"),
          new ScrubPattern(
              Pattern.compile("x-api-key[\\s:=]+([^\\s,;]+)", Pattern.CASE_INSENSITIVE),
              "x-api-key: ***REDACTED***"));

  @Override
  public FilterReply decide(ILoggingEvent event) {
    if (event == null) {
      return FilterReply.NEUTRAL;
    }
    String message = event.getFormattedMessage();
    if (message == null) {
      return FilterReply.NEUTRAL;
    }
    ScrubResult scrubResult = scrubSecrets(message);
    if (!message.contains(TOKEN) && !scrubResult.changed()) {
      return FilterReply.NEUTRAL;
    }
    if (scrubResult.changed()) {
      replaceFormattedMessage(event, scrubResult.message());
    }
    stampScrubbed(event, scrubResult.changed() ? "secret_token" : "sensitive_marker");
    return FilterReply.NEUTRAL;
  }

  private static ScrubResult scrubSecrets(String message) {
    String scrubbedMessage = message;
    for (ScrubPattern scrubPattern : SECRET_PATTERNS) {
      scrubbedMessage =
          scrubPattern.pattern().matcher(scrubbedMessage).replaceAll(scrubPattern.replacement());
    }
    return new ScrubResult(scrubbedMessage, !scrubbedMessage.equals(message));
  }

  private static void replaceFormattedMessage(ILoggingEvent event, String scrubbedMessage) {
    if (!(event instanceof LoggingEvent classic)) {
      return;
    }
    try {
      Field formattedMessageField = LoggingEvent.class.getDeclaredField("formattedMessage");
      formattedMessageField.setAccessible(true);
      formattedMessageField.set(classic, scrubbedMessage);
    } catch (NoSuchFieldException | IllegalAccessException reflectionException) {
      throw new IllegalStateException(
          "Logback LoggingEvent layout changed; SensitiveMarkerScrubFilter needs update",
          reflectionException);
    }
  }

  private static void stampScrubbed(ILoggingEvent event, String scrubReason) {
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
    copy.put("scrub_reason", scrubReason);
    try {
      Field mdcPropertyMapField = LoggingEvent.class.getDeclaredField("mdcPropertyMap");
      mdcPropertyMapField.setAccessible(true);
      mdcPropertyMapField.set(classic, copy);
    } catch (NoSuchFieldException | IllegalAccessException reflectionException) {
      // If the Logback layout ever changes the field name we want to know about it
      // loudly during tests/CI, but never break a production log emission for it.
      throw new IllegalStateException(
          "Logback LoggingEvent layout changed; SensitiveMarkerScrubFilter needs update",
          reflectionException);
    }
  }

  private record ScrubPattern(Pattern pattern, String replacement) {}

  private record ScrubResult(String message, boolean changed) {}
}
