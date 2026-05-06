package com.zeromail.worker.billing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.zeromail.core.billing.service.BillingProperties;

/**
 * Worker-side activation of {@link BillingProperties}. The worker boot class scans
 * {@code com.zeromail.core}, so no extra component, entity, or repository scanning belongs here.
 */
@Configuration
@EnableConfigurationProperties(BillingProperties.class)
public class BillingWorkerConfiguration {
}
