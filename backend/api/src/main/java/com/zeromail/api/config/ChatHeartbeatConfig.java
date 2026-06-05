package com.zeromail.api.config;

import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ChatHeartbeatConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatHeartbeatConfig.class);

    @Bean(name = "chatHeartbeatTaskScheduler")
    public TaskScheduler chatHeartbeatTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setThreadNamePrefix("chat-heartbeat-");
        taskScheduler.setPoolSize(2);
        taskScheduler.setErrorHandler(ChatHeartbeatConfig::handleHeartbeatError);
        taskScheduler.initialize();
        return taskScheduler;
    }

    /**
     * Custom error handler for the SSE keepalive scheduler. Without it, Spring's default {@code
     * TaskUtils.LoggingErrorHandler} logs every heartbeat failure at ERROR — but the overwhelming
     * majority are simply the chat client closing the SSE connection (user navigated away / closed
     * the tab) while a keepalive frame was in flight, which surfaces as a broken-pipe {@link
     * IOException} wrapped in an {@code IllegalStateException}. That is the normal end-of-stream
     * path, not an actionable server fault, so it is logged at DEBUG; anything else stays at WARN.
     */
    private static void handleHeartbeatError(Throwable heartbeatFailure) {
        if (isClientDisconnect(heartbeatFailure)) {
            log.debug("event=chat_heartbeat_client_disconnected");
            return;
        }
        log.warn(
                "event=chat_heartbeat_failed failureType={}",
                heartbeatFailure.getClass().getSimpleName());
    }

    private static boolean isClientDisconnect(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth < 6) {
            String simpleName = current.getClass().getSimpleName();
            if ("AsyncRequestNotUsableException".equals(simpleName)
                    || "ClientAbortException".equals(simpleName)) {
                return true;
            }
            if (current instanceof IOException && hasDisconnectMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private static boolean hasDisconnectMessage(String message) {
        if (message == null) {
            return false;
        }
        String lowerCaseMessage = message.toLowerCase(Locale.ROOT);
        return lowerCaseMessage.contains("broken pipe")
                || lowerCaseMessage.contains("connection reset")
                || lowerCaseMessage.contains("outbound has closed");
    }
}
