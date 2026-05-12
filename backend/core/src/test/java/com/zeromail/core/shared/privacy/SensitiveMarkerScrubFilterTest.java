package com.zeromail.core.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Verifies the per-event filter: - clean events stay clean (no scrubbed marker carried over from
 * prior sensitive events), - sensitive events carry {@code scrubbed=true} on their OWN MDC
 * snapshot, - the redaction contract (Sensitive.toString) still produces ***REDACTED*** content.
 *
 * <p>The filter is wired against the appender (not as a TurboFilter) so it sees fully built {@link
 * ILoggingEvent}s and mutates only that event's per-event MDC map.
 */
class SensitiveMarkerScrubFilterTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(SensitiveMarkerScrubFilterTest.class);
        appender = new ListAppender<>();
        appender.addFilter(new SensitiveMarkerScrubFilter());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        appender.stop();
        logger.detachAppender(appender);
    }

    @Test
    void sensitive_argument_is_scrubbed() {
        Sensitive<String> s = Sensitive.of("plaintext-secret");
        logger.info("token={}", s);
        assertThat(appender.list)
                .anySatisfy(
                        ev -> {
                            assertThat(ev.getFormattedMessage()).doesNotContain("plaintext-secret");
                            assertThat(ev.getFormattedMessage()).contains("***REDACTED***");
                        });
    }

    @Test
    void raw_sensitive_token_triggers_scrub() {
        // Simulate a class that bypassed toString() and emitted the literal `Sensitive(...)` form.
        // The per-event filter inspects the formatted message and stamps the marker into the
        // event's own MDC map (not thread-local MDC).
        logger.info("raw={}", "Sensitive(foo)");
        assertThat(appender.list)
                .anySatisfy(
                        ev -> {
                            assertThat(ev.getMDCPropertyMap()).containsEntry("scrubbed", "true");
                            assertThat(ev.getMDCPropertyMap())
                                    .containsEntry("scrub_reason", "sensitive_marker");
                        });
    }

    @Test
    void clean_message_does_not_stamp_marker() {
        logger.info("safe message with no token");
        assertThat(appender.list)
                .allSatisfy(ev -> assertThat(ev.getMDCPropertyMap()).doesNotContainKey("scrubbed"));
    }

    @Test
    void scrub_marker_does_not_leak_into_subsequent_events() {
        // Regression test for WR-05: under the previous TurboFilter implementation, a
        // sensitive event would stamp MDC for the rest of the thread/request, marking
        // every subsequent clean event as scrubbed=true. The per-event filter must NOT.
        logger.info("raw={}", "Sensitive(foo)");
        logger.info("subsequent clean event with no token");

        assertThat(appender.list).hasSizeGreaterThanOrEqualTo(2);
        ILoggingEvent first = appender.list.get(0);
        ILoggingEvent second = appender.list.get(1);
        assertThat(first.getMDCPropertyMap()).containsEntry("scrubbed", "true");
        assertThat(second.getMDCPropertyMap()).doesNotContainKey("scrubbed");
        assertThat(second.getMDCPropertyMap()).doesNotContainKey("scrub_reason");
    }
}
