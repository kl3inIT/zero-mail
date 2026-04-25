package com.zeromail.core.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class SensitiveMarkerScrubFilterTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(SensitiveMarkerScrubFilterTest.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.getLoggerContext().addTurboFilter(new SensitiveMarkerScrubFilter());
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
        assertThat(appender.list).anySatisfy(ev -> {
            assertThat(ev.getFormattedMessage()).doesNotContain("plaintext-secret");
            assertThat(ev.getFormattedMessage()).contains("***REDACTED***");
        });
    }

    @Test
    void raw_sensitive_token_triggers_scrub() {
        // Simulate a class that bypassed toString() and emitted the literal `Sensitive(...)` form.
        // The filter cannot rewrite the rendered message (Logback's single-arg dispatch passes a
        // transient param array to TurboFilters), so the redaction contract is delivered via
        // Sensitive.toString(); this filter's job is the observable MDC marker for SOC alerting.
        logger.info("raw={}", "Sensitive(foo)");
        assertThat(appender.list).anySatisfy(ev -> {
            assertThat(ev.getMDCPropertyMap()).containsEntry("scrubbed", "true");
            assertThat(ev.getMDCPropertyMap()).containsEntry("scrub_reason", "sensitive_marker");
        });
    }

    @Test
    void clean_message_does_not_stamp_marker() {
        logger.info("safe message with no token");
        assertThat(appender.list).allSatisfy(ev ->
                assertThat(ev.getMDCPropertyMap()).doesNotContainKey("scrubbed"));
    }
}
