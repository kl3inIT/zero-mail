package com.zeromail.core.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import com.zeromail.core.shared.privacy.SensitiveMarkerScrubFilter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class LogbackScrubExtensionTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LogbackScrubExtensionTest.class);
        appender = new ListAppender<>();
        appender.addFilter(new SensitiveMarkerScrubFilter());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        appender.stop();
        logger.detachAppender(appender);
    }

    @ParameterizedTest
    @CsvSource({
            "event=probe apiKey=sk-ant-abc123, apiKey=***REDACTED***, sk-ant-abc123",
            "event=probe Authorization=Bearer sk-or-v1-xyz, Bearer ***REDACTED***, sk-or-v1-xyz",
            "event=probe x-api-key: sk-openrouter-secret, x-api-key: ***REDACTED***, sk-openrouter-secret"
    })
    void scrubber_redacts_llm_secret_tokens(
            String rawLogMessage,
            String expectedRedactedFragment,
            String forbiddenSecretValue) {
        logger.info(rawLogMessage);

        assertThat(appender.list).singleElement().satisfies(loggingEvent -> {
            assertThat(loggingEvent.getFormattedMessage())
                    .contains(expectedRedactedFragment)
                    .doesNotContain(forbiddenSecretValue);
            assertThat(loggingEvent.getMDCPropertyMap()).containsEntry("scrubbed", "true");
        });
    }
}
