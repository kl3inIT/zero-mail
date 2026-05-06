package com.zeromail.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.zeromail.core.billing.service.BillingProperties;

/**
 * Activates zero-mail.billing.* configuration binding inside the api module. The api boot class
 * already component-scans com.zeromail.core, so no additional component scan is needed here.
 */
@Configuration
@EnableConfigurationProperties(BillingProperties.class)
public class BillingApiConfiguration {}
