package com.zeromail.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HealthcheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthcheckScheduler.class);

    @Scheduled(fixedRate = 60_000L)
    public void tick() {
        log.info("event=worker_healthcheck_tick");
    }
}
