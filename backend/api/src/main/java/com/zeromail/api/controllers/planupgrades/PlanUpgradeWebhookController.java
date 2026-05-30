package com.zeromail.api.controllers.planupgrades;

import com.zeromail.core.billing.usecases.LemonSqueezyWebhookIngestResult;
import com.zeromail.core.billing.usecases.LemonSqueezyWebhookIngestService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plan-upgrades/webhooks")
public class PlanUpgradeWebhookController {

    private static final String EVENT_ID_HEADER = "X-Event-Id";
    private static final String LEMON_SQUEEZY_EVENT_ID_HEADER = "X-LemonSqueezy-Event-Id";

    private final LemonSqueezyWebhookIngestService lemonSqueezyWebhookIngestService;

    public PlanUpgradeWebhookController(
            LemonSqueezyWebhookIngestService lemonSqueezyWebhookIngestService) {
        this.lemonSqueezyWebhookIngestService = lemonSqueezyWebhookIngestService;
    }

    @PostMapping("/lemon-squeezy")
    public ResponseEntity<Void> lemonSqueezy(
            @RequestBody(required = false) String payload, @RequestHeader HttpHeaders headers) {
        LemonSqueezyWebhookIngestResult ingestResult =
                lemonSqueezyWebhookIngestService.ingest(payload, eventId(headers));
        System.out.println(
                "event=lemon_squeezy_webhook_received eventId="
                        + ingestResult.eventId()
                        + " eventName="
                        + ingestResult.eventName()
                        + " payload="
                        + ingestResult.redactedPayloadJson());
        return ResponseEntity.ok().build();
    }

    private String eventId(HttpHeaders headers) {
        String eventId = headers.getFirst(EVENT_ID_HEADER);
        return eventId == null || eventId.isBlank()
                ? headers.getFirst(LEMON_SQUEEZY_EVENT_ID_HEADER)
                : eventId;
    }
}
