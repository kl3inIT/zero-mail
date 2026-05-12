package com.zeromail.worker.billing;

import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Marks unpaid top-up intents as expired after their configured expiry time passes. */
@Component
public class BillingIntentExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(BillingIntentExpirySweeper.class);

    private final BillingTopupIntentRepository intentRepository;

    public BillingIntentExpirySweeper(BillingTopupIntentRepository intentRepository) {
        this.intentRepository = intentRepository;
    }

    @Scheduled(fixedRate = 3_600_000L)
    @SchedulerLock(
            name = "billingIntentExpirySweeper",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT10M")
    public void scheduledSweep() {
        sweep();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void sweep() {
        int rowsExpired = intentRepository.expireStale(Instant.now());
        if (rowsExpired > 0) {
            log.info("event=billing_intent_expiry_sweep rowsExpired={}", rowsExpired);
        }
    }
}
