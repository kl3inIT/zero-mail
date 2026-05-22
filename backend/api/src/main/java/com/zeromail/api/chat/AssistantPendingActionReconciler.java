package com.zeromail.api.chat;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.chat.persistence.lowlevel.AssistantReconciliationRepository;
import com.zeromail.core.chat.projection.ExpiredAssistantPendingAction;
import com.zeromail.core.chat.projection.StaleAssistantSendAudit;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reconciles assistant confirmation state after crashes. All SQL lives in {@link
 * AssistantReconciliationRepository}; this scheduled component composes the sweep and decides
 * terminal state from Gmail probes.
 *
 * <p>There is one permanent residual gap: Gmail can accept a generated message after the server has
 * failed to commit the SEND_IN_FLIGHT audit row to Postgres. Without a committed audit row,
 * server-side reconciliation has no durable key to discover that orphaned delivery; this is
 * inherent to coordinating Gmail and Postgres without two-phase commit.
 */
@Component
public class AssistantPendingActionReconciler {

    private static final Logger log =
            LoggerFactory.getLogger(AssistantPendingActionReconciler.class);
    private static final int PAGE_SIZE = 100;
    private static final long SEND_IN_FLIGHT_GRACE_SECONDS = 60;

    private final AssistantReconciliationRepository assistantReconciliationRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final Clock clock;
    private final Counter residualLeasesCounter;
    private final Counter auditStateMismatchCounter;
    private final Counter sendRecoveredCounter;
    private final Counter sendLostCounter;

    @Autowired
    public AssistantPendingActionReconciler(
            AssistantReconciliationRepository assistantReconciliationRepository,
            GmailApiClientFactory gmailApiClientFactory,
            MeterRegistry meterRegistry) {
        this(
                assistantReconciliationRepository,
                gmailApiClientFactory,
                meterRegistry,
                Clock.systemUTC());
    }

    AssistantPendingActionReconciler(
            AssistantReconciliationRepository assistantReconciliationRepository,
            GmailApiClientFactory gmailApiClientFactory,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.assistantReconciliationRepository = assistantReconciliationRepository;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.clock = clock;
        this.residualLeasesCounter =
                Counter.builder("chat_reconciliation_residual_leases_total")
                        .description("Expired assistant pending-action processing leases swept")
                        .register(meterRegistry);
        this.auditStateMismatchCounter =
                Counter.builder("chat_reconciliation_audit_vs_state_mismatch_total")
                        .description("Pending actions reconciled from committed audit rows")
                        .register(meterRegistry);
        this.sendRecoveredCounter =
                Counter.builder("chat_reconciliation_send_recovered_total")
                        .description("Stale assistant sends recovered as committed from Gmail")
                        .register(meterRegistry);
        this.sendLostCounter =
                Counter.builder("chat_reconciliation_send_lost_total")
                        .description("Stale assistant sends marked failed after Gmail lookup miss")
                        .register(meterRegistry);
    }

    @PostConstruct
    void logStartupResidualGap() {
        log.info("event=chat_reconciler_started tenantId=- note=residual-gap-orphaned-email");
    }

    @Scheduled(fixedRate = 300_000L, initialDelay = 60_000L)
    public void reconcile() {
        reconcileExpiredLeases();
        reconcileStaleSendInFlight();
    }

    void reconcileExpiredLeases() {
        List<ExpiredAssistantPendingAction> expiredPendingActions =
                assistantReconciliationRepository.findExpiredPendingActions(
                        clock.instant(), PAGE_SIZE);
        for (ExpiredAssistantPendingAction expiredPendingAction : expiredPendingActions) {
            TenantContext.runWith(
                    expiredPendingAction.tenantId(),
                    () -> reconcileExpiredLease(expiredPendingAction));
        }
    }

    void reconcileStaleSendInFlight() {
        List<StaleAssistantSendAudit> staleSendAudits =
                assistantReconciliationRepository.findStaleSendAudits(
                        clock.instant().minusSeconds(SEND_IN_FLIGHT_GRACE_SECONDS), PAGE_SIZE);
        for (StaleAssistantSendAudit staleSendAudit : staleSendAudits) {
            TenantContext.runWith(
                    staleSendAudit.tenantId(), () -> reconcileStaleSendAudit(staleSendAudit));
        }
    }

    private void reconcileExpiredLease(ExpiredAssistantPendingAction expiredPendingAction) {
        residualLeasesCounter.increment();
        boolean committedAuditExists =
                assistantReconciliationRepository.committedAuditExists(
                        expiredPendingAction.tenantId(),
                        expiredPendingAction.chatId(),
                        expiredPendingAction.toolCallId());
        String targetState = committedAuditExists ? "CONFIRMED" : "FAILED";
        int changedRows =
                assistantReconciliationRepository.sweepPendingActionToTerminalState(
                        expiredPendingAction.pendingActionId(),
                        expiredPendingAction.tenantId(),
                        targetState);
        if (changedRows == 0) {
            return;
        }
        if (committedAuditExists) {
            auditStateMismatchCounter.increment();
        }
        log.info(
                "event=chat_reconciliation_swept tenantId={} chatId={} sweep=expired-lease action={}",
                expiredPendingAction.tenantId(),
                expiredPendingAction.chatId(),
                committedAuditExists ? "confirmed" : "failed");
    }

    private void reconcileStaleSendAudit(StaleAssistantSendAudit staleSendAudit) {
        try {
            if (gmailHasMessage(staleSendAudit)) {
                markSendCommitted(staleSendAudit);
            } else {
                markSendFailed(staleSendAudit);
            }
        } catch (IOException gmailLookupFailure) {
            log.warn(
                    "event=chat_reconciliation_gmail_lookup_failed tenantId={} chatId={} errorClass={}",
                    staleSendAudit.tenantId(),
                    staleSendAudit.chatId(),
                    gmailLookupFailure.getClass().getSimpleName());
        }
    }

    private boolean gmailHasMessage(StaleAssistantSendAudit staleSendAudit) throws IOException {
        Gmail gmail = gmailApiClientFactory.buildClientForTenant(staleSendAudit.tenantId());
        ListMessagesResponse response =
                gmail.users()
                        .messages()
                        .list("me")
                        .setQ("rfc822msgid:" + staleSendAudit.gmailMessageId())
                        .execute();
        List<Message> messages = response == null ? null : response.getMessages();
        return messages != null && !messages.isEmpty();
    }

    private void markSendCommitted(StaleAssistantSendAudit staleSendAudit) {
        Instant sentAt = clock.instant();
        int changedRows =
                assistantReconciliationRepository.markSendCommitted(
                        staleSendAudit.auditId(), staleSendAudit.tenantId(), sentAt);
        if (changedRows == 0) {
            return;
        }
        assistantReconciliationRepository.updatePendingActionState(
                staleSendAudit.tenantId(),
                staleSendAudit.chatId(),
                staleSendAudit.toolCallId(),
                "CONFIRMED");
        sendRecoveredCounter.increment();
        log.info(
                "event=chat_reconciliation_swept tenantId={} chatId={} sweep=send-in-flight action=committed",
                staleSendAudit.tenantId(),
                staleSendAudit.chatId());
    }

    private void markSendFailed(StaleAssistantSendAudit staleSendAudit) {
        int changedRows =
                assistantReconciliationRepository.markSendFailed(
                        staleSendAudit.auditId(), staleSendAudit.tenantId());
        if (changedRows == 0) {
            return;
        }
        assistantReconciliationRepository.updatePendingActionState(
                staleSendAudit.tenantId(),
                staleSendAudit.chatId(),
                staleSendAudit.toolCallId(),
                "FAILED");
        sendLostCounter.increment();
        log.info(
                "event=chat_reconciliation_swept tenantId={} chatId={} sweep=send-in-flight action=failed",
                staleSendAudit.tenantId(),
                staleSendAudit.chatId());
    }
}
