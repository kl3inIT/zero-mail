package com.zeromail.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.zeromail.core.llm.model.SafetyViolationException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class GlobalExceptionHandlerLogContentTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        appender.stop();
        logger.detachAppender(appender);
    }

    @Test
    void handler_log_does_not_contain_exception_message() {
        String sentinel = "SENTINEL_EXCEPTION_MESSAGE_NEVER_LOGGED_K8M2";
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        handler.onSafetyViolation(new SafetyViolationException(sentinel));

        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list).allSatisfy(loggingEvent -> {
            assertThat(loggingEvent.getFormattedMessage()).doesNotContain(sentinel);
            assertThat(loggingEvent.getFormattedMessage()).contains("event=");
            assertThat(loggingEvent.getFormattedMessage()).contains("exceptionClass=SafetyViolationException");
        });
    }
}
