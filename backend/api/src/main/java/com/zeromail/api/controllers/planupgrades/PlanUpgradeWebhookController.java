package com.zeromail.api.controllers.planupgrades;

import com.zeromail.core.billing.usecases.LemonSqueezyWebhookIngestResult;
import com.zeromail.core.billing.usecases.LemonSqueezyWebhookIngestService;
import com.zeromail.core.billing.usecases.SepayWebhookIngestService;
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
    private final SepayWebhookIngestService sepayWebhookIngestService;

    public PlanUpgradeWebhookController(
            LemonSqueezyWebhookIngestService lemonSqueezyWebhookIngestService,
            SepayWebhookIngestService sepayWebhookIngestService) {
        this.lemonSqueezyWebhookIngestService = lemonSqueezyWebhookIngestService;
        this.sepayWebhookIngestService = sepayWebhookIngestService;
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

    @PostMapping("/sepay")
    public ResponseEntity<java.util.Map<String, Boolean>> sepay(
            @RequestBody(required = false) String payload) {
        sepayWebhookIngestService.ingest(payload);
        return ResponseEntity.ok(java.util.Map.of("success", true));
    }

    private String eventId(HttpHeaders headers) {
        String eventId = headers.getFirst(EVENT_ID_HEADER);
        return eventId == null || eventId.isBlank()
                ? headers.getFirst(LEMON_SQUEEZY_EVENT_ID_HEADER)
                : eventId;
    }
}
