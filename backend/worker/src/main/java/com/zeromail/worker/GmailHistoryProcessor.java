package com.zeromail.worker;

import com.zeromail.core.gmail.persistence.PubSubDeliveryEntity;
import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.gmail.usecases.GmailDeliveryProcessingService;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GmailHistoryProcessor {

    private static final int BATCH_SIZE = 50;
    private static final int LOCK_SECONDS = 120;

    private final PubSubDeliveryRepository deliveryRepository;
    private final GmailDeliveryProcessingService deliveryProcessingService;

    public GmailHistoryProcessor(
            PubSubDeliveryRepository deliveryRepository,
            GmailDeliveryProcessingService deliveryProcessingService) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryProcessingService = deliveryProcessingService;
    }

    @Scheduled(fixedDelay = 1_000L)
    public void tick() {
        List<PubSubDeliveryEntity> batch =
                deliveryRepository.claimPendingBatch(BATCH_SIZE, LOCK_SECONDS);
        for (PubSubDeliveryEntity delivery : batch) {
            ScopedValue.where(TenantContext.TENANT, delivery.getTenantId().toString())
                    .run(() -> deliveryProcessingService.processDelivery(delivery));
        }
    }
}
