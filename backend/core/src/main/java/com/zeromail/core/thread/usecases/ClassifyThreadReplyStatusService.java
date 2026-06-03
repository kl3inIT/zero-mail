package com.zeromail.core.thread.usecases;

import com.zeromail.core.gmail.event.MailOutboundObserved;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.domain.ThreadReplyStatus;
import com.zeromail.core.thread.persistence.ThreadReplyStatusEntity;
import com.zeromail.core.thread.persistence.ThreadReplyStatusRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClassifyThreadReplyStatusService {

    private static final Logger log =
            LoggerFactory.getLogger(ClassifyThreadReplyStatusService.class);

    private final ThreadReplyStatusRepository threadReplyStatusRepository;
    private final Clock clock;
    private final TransactionTemplate classificationTransaction;
    private final TransactionTemplate listenerClassificationTransaction;

    @Autowired
    public ClassifyThreadReplyStatusService(
            ThreadReplyStatusRepository threadReplyStatusRepository,
            PlatformTransactionManager transactionManager) {
        this(
                threadReplyStatusRepository,
                Clock.systemUTC(),
                classificationTransaction(
                        transactionManager, TransactionDefinition.PROPAGATION_REQUIRED),
                classificationTransaction(
                        transactionManager, TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    public ClassifyThreadReplyStatusService(
            ThreadReplyStatusRepository threadReplyStatusRepository, Clock clock) {
        this(threadReplyStatusRepository, clock, null, null);
    }

    private ClassifyThreadReplyStatusService(
            ThreadReplyStatusRepository threadReplyStatusRepository,
            Clock clock,
            TransactionTemplate classificationTransaction,
            TransactionTemplate listenerClassificationTransaction) {
        this.threadReplyStatusRepository =
                Objects.requireNonNull(
                        threadReplyStatusRepository,
                        "threadReplyStatusRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.classificationTransaction = classificationTransaction;
        this.listenerClassificationTransaction = listenerClassificationTransaction;
    }

    public ThreadReplyStatus classify(ThreadReplyClassificationInput classificationInput) {
        Objects.requireNonNull(classificationInput, "classificationInput must not be null");
        return ScopedValue.where(TenantContext.TENANT, classificationInput.tenantId().toString())
                .call(() -> classifyInTransaction(classificationInput, false));
    }

    /**
     * Force re-classification even when the thread's latest message was already classified. Used by
     * the operator-triggered backfill re-sync so improved classifier logic re-applies to threads
     * seen before. Unlike a new message arriving, this preserves the existing {@code resolved}
     * flag.
     */
    public ThreadReplyStatus reclassify(ThreadReplyClassificationInput classificationInput) {
        Objects.requireNonNull(classificationInput, "classificationInput must not be null");
        return ScopedValue.where(TenantContext.TENANT, classificationInput.tenantId().toString())
                .call(() -> classifyInTransaction(classificationInput, true));
    }

    public Optional<String> currentDraftId(String gmailThreadId) {
        return threadReplyStatusRepository
                .findByGmailThreadId(gmailThreadId)
                .flatMap(threadReplyStatus -> Optional.ofNullable(threadReplyStatus.getDraftId()));
    }

    @ApplicationModuleListener
    public void on(MailOutboundObserved event) {
        ThreadReplyClassificationInput classificationInput =
                new ThreadReplyClassificationInput(
                        event.tenantId(),
                        event.gmailThreadId(),
                        event.gmailMessageId(),
                        true,
                        true,
                        false,
                        null,
                        false,
                        // Outbound: reader sent the latest message, so the needs-reply signal is
                        // irrelevant (bucketFor takes the AWAITING_THEIR_REPLY branch).
                        false);
        TenantContext.runWith(
                event.tenantId(), () -> classifyListenerEventInTransaction(classificationInput));
    }

    private ThreadReplyStatus classifyWithTenantBound(
            ThreadReplyClassificationInput classificationInput, boolean forceReclassify) {
        Optional<ThreadReplyStatusEntity> existingStatus =
                threadReplyStatusRepository.findByGmailThreadId(
                        classificationInput.gmailThreadId());
        boolean sameMessageAlreadyClassified =
                existingStatus
                        .map(ThreadReplyStatusEntity::getLastClassifiedMessageId)
                        .filter(classificationInput.lastMessageId()::equals)
                        .isPresent();
        if (sameMessageAlreadyClassified && !forceReclassify) {
            return toDomain(existingStatus.orElseThrow());
        }

        ThreadReplyBucket bucket = bucketFor(classificationInput);
        Instant classifiedAt = clock.instant();
        ThreadReplyStatusEntity statusEntity =
                existingStatus.orElseGet(
                        () ->
                                new ThreadReplyStatusEntity(
                                        UUID.randomUUID(),
                                        classificationInput.tenantId(),
                                        classificationInput.gmailThreadId(),
                                        bucket,
                                        classificationInput.lastMessageId(),
                                        classifiedAt,
                                        classificationInput.hasZeroMailDraft(),
                                        classificationInput.zeroMailDraftId(),
                                        false));
        statusEntity.setBucket(bucket);
        statusEntity.setLastClassifiedMessageId(classificationInput.lastMessageId());
        statusEntity.setLastClassifiedAt(classifiedAt);
        statusEntity.setHasDraft(classificationInput.hasZeroMailDraft());
        statusEntity.setDraftId(classificationInput.zeroMailDraftId());
        if (existingStatus.isPresent() && !sameMessageAlreadyClassified) {
            // A genuinely new message reopens the thread; a forced re-sync of the same message
            // keeps
            // whatever resolved state the reader already chose.
            statusEntity.setResolved(false);
        }

        ThreadReplyStatusEntity savedStatus = threadReplyStatusRepository.save(statusEntity);
        log.info(
                "event=thread_reply_classified tenantId={} gmailThreadId={} bucket={}",
                classificationInput.tenantId(),
                classificationInput.gmailThreadId(),
                bucket);
        return toDomain(savedStatus);
    }

    private ThreadReplyStatus classifyInTransaction(
            ThreadReplyClassificationInput classificationInput, boolean forceReclassify) {
        if (classificationTransaction == null) {
            return classifyWithTenantBound(classificationInput, forceReclassify);
        }
        ThreadReplyStatus classifiedStatus =
                classificationTransaction.execute(
                        _ -> classifyWithTenantBound(classificationInput, forceReclassify));
        return Objects.requireNonNull(classifiedStatus, "classifiedStatus must not be null");
    }

    private void classifyListenerEventInTransaction(
            ThreadReplyClassificationInput classificationInput) {
        if (listenerClassificationTransaction == null) {
            classifyWithTenantBound(classificationInput, false);
            return;
        }
        listenerClassificationTransaction.executeWithoutResult(
                _ -> classifyWithTenantBound(classificationInput, false));
    }

    private static TransactionTemplate classificationTransaction(
            PlatformTransactionManager transactionManager, int propagationBehavior) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(propagationBehavior);
        return transactionTemplate;
    }

    private static ThreadReplyBucket bucketFor(ThreadReplyClassificationInput classificationInput) {
        if (classificationInput.lastMessageFromIsTenant()
                && classificationInput.threadHasSentLabel()
                && !classificationInput.lastMessageIsAutoReply()) {
            // The reader sent the latest message and is waiting on the other party.
            return ThreadReplyBucket.AWAITING_THEIR_REPLY;
        }
        // Otherwise the caller's needs-reply signal (LLM classification / no-reply pre-filter / "a
        // reply was already drafted") decides between the TO_REPLY ("Cần trả lời") and FYI buckets.
        return classificationInput.inboundReplyNeeded()
                ? ThreadReplyBucket.TO_REPLY
                : ThreadReplyBucket.FYI;
    }

    private static ThreadReplyStatus toDomain(ThreadReplyStatusEntity statusEntity) {
        return new ThreadReplyStatus(
                statusEntity.getTenantId(),
                statusEntity.getGmailThreadId(),
                statusEntity.getBucket(),
                statusEntity.getLastClassifiedMessageId(),
                statusEntity.getLastClassifiedAt(),
                statusEntity.hasDraft(),
                statusEntity.getDraftId(),
                statusEntity.isResolved());
    }
}
