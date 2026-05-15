package com.zeromail.worker.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ErrorHandler;

/**
 * Replaces Spring Boot's default task scheduler with one whose {@link ErrorHandler} emits the
 * project's structured {@code event=scheduled_job_failed} log line and increments a Micrometer
 * counter. Spring's default behaviour swallows scheduled task failures with a single WARN line,
 * which hides silent task failures from dashboards and alerts.
 */
@Configuration
public class ScheduledTaskErrorHandlerConfig {

    private static final Logger log =
            LoggerFactory.getLogger(ScheduledTaskErrorHandlerConfig.class);

    @Bean
    public TaskScheduler taskScheduler(MeterRegistry meterRegistry) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors()));
        scheduler.setThreadNamePrefix("zm-scheduled-");
        scheduler.setErrorHandler(buildErrorHandler(meterRegistry));
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    private static ErrorHandler buildErrorHandler(MeterRegistry meterRegistry) {
        return throwable -> {
            String exceptionType = throwable.getClass().getSimpleName();
            meterRegistry
                    .counter("worker.scheduled.error", "exceptionType", exceptionType)
                    .increment();
            log.error(
                    "event=scheduled_job_failed reason={} message={}",
                    exceptionType,
                    safeMessage(throwable));
        };
    }

    private static String safeMessage(Throwable throwable) {
        // Length-bounded to avoid leaking large stack details into the structured log line.
        String raw = throwable.getMessage();
        if (raw == null) return "";
        return raw.length() > 200 ? raw.substring(0, 200) : raw;
    }
}
