package com.zeromail.api.websocket;

import com.zeromail.core.billing.event.BillingTopupCredited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BillingWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(BillingWebSocketPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public BillingWebSocketPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBillingTopupCredited(BillingTopupCredited event) {
        String destination = "/topic/tenants/" + event.tenantId() + "/billing";
        messagingTemplate.convertAndSend(destination, BillingTopupCreditedMessage.from(event));
        log.info("event=billing_topup_websocket_sent tenantId={}", event.tenantId());
    }
}
