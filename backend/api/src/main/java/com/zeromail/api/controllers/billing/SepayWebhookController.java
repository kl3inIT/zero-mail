package com.zeromail.api.controllers.billing;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.billing.SepayWebhookPayload;
import com.zeromail.core.billing.service.BillingTopupService;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SePay webhook receiver. API-key authentication is enforced by {@code
 * BillingWebhookSecurityConfig}; this controller remains visible in OpenAPI so internal tooling can
 * use the generated typed client for replay and verification workflows.
 */
@RestController
@Tag(name = "billing-webhook")
public class SepayWebhookController {

  private static final Logger log = LoggerFactory.getLogger(SepayWebhookController.class);

  private final BillingTopupService billingTopupService;

  public SepayWebhookController(BillingTopupService billingTopupService) {
    this.billingTopupService = billingTopupService;
  }

  @PostMapping("/api/billing/sepay/webhook")
  public Map<String, Object> receive(@RequestBody SepayWebhookPayload payload) {
    log.info("event=sepay_webhook_received");
    billingTopupService.applyWebhook(
        payload.id(),
        payload.code(),
        payload.referenceCode(),
        payload.content(),
        payload.transferType(),
        payload.transferAmount());
    return Map.of("success", true);
  }
}
