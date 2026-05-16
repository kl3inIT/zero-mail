package com.zeromail.worker.billing;

import com.zeromail.core.billing.persistence.BillingPaymentAttemptRepository;
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
    private final BillingPaymentAttemptRepository paymentAttemptRepository;

    public BillingIntentExpirySweeper(
            BillingTopupIntentRepository intentRepository,
            BillingPaymentAttemptRepository paymentAttemptRepository) {
        this.intentRepository = intentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
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
        Instant now = Instant.now();
        int intentsExpired = intentRepository.expireStale(now);
        int attemptsExpired = paymentAttemptRepository.expireStale(now);
        if (intentsExpired > 0 || attemptsExpired > 0) {
            log.info(
                    "event=billing_intent_expiry_sweep intentsExpired={} attemptsExpired={}",
                    intentsExpired,
                    attemptsExpired);
        }
    }
}
