package com.zeromail.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Gates {@code @EnableScheduling} for the API module behind {@code zero-mail.scheduling.enabled}.
 *
 * <p>Default ON for dev/prod (matchIfMissing=true). The {@code test} profile sets this to {@code
 * false} so {@code @Scheduled} cron beans (e.g. {@code AssistantPendingActionReconciler}) do not
 * fire in test JVMs, eliminating background thread allocation and timing flake during
 * {@code @SpringBootTest} runs.
 *
 * <p>Tests that need to exercise scheduling can re-enable it with
 * {@code @TestPropertySource(properties = "zero-mail.scheduling.enabled=true")}.
 */
@Configuration
@ConditionalOnProperty(
        name = "zero-mail.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {}
