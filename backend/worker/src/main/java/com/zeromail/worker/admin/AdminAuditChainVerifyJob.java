package com.zeromail.worker.admin;

import com.zeromail.core.admin.audit.usecases.AdminAuditChainVerifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.OptionalLong;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AdminAuditChainVerifyJob {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditChainVerifyJob.class);

    private final AdminAuditChainVerifier adminAuditChainVerifier;
    private final Counter mismatchCounter;

    public AdminAuditChainVerifyJob(
            AdminAuditChainVerifier adminAuditChainVerifier, MeterRegistry meterRegistry) {
        this.adminAuditChainVerifier = adminAuditChainVerifier;
        mismatchCounter =
                Counter.builder("zero_mail.admin.audit_chain.mismatch_total")
                        .description("Admin audit HMAC chain verification mismatches")
                        .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "adminAuditChainVerify", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void scheduledVerify() {
        verifyOnce();
    }

    public OptionalLong verifyOnce() {
        OptionalLong firstMismatch = adminAuditChainVerifier.verifyOnce();
        if (firstMismatch.isPresent()) {
            mismatchCounter.increment();
            log.error(
                    "event=admin_audit_chain_mismatch tenantId=system chainIndex={}",
                    firstMismatch.orElseThrow());
        } else {
            log.info("event=admin_audit_chain_verified tenantId=system status=ok");
        }
        return firstMismatch;
    }
}
