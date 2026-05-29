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

    @Autowired
    public ClassifyThreadReplyStatusService(
            ThreadReplyStatusRepository threadReplyStatusRepository,
            PlatformTransactionManager transactionManager) {
        this(
                threadReplyStatusRepository,
                Clock.systemUTC(),
                classificationTransaction(transactionManager));
    }

    public ClassifyThreadReplyStatusService(
            ThreadReplyStatusRepository threadReplyStatusRepository, Clock clock) {
        this(threadReplyStatusRepository, clock, null);
    }

    private ClassifyThreadReplyStatusService(
            ThreadReplyStatusRepository threadReplyStatusRepository,
            Clock clock,
            TransactionTemplate classificationTransaction) {
        this.threadReplyStatusRepository =
                Objects.requireNonNull(
                        threadReplyStatusRepository,
                        "threadReplyStatusRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.classificationTransaction = classificationTransaction;
    }

    public ThreadReplyStatus classify(ThreadReplyClassificationInput classificationInput) {
        Objects.requireNonNull(classificationInput, "classificationInput must not be null");
        return ScopedValue.where(TenantContext.TENANT, classificationInput.tenantId().toString())
                .call(() -> classifyInTransaction(classificationInput));
    }

    public Optional<String> currentDraftId(String gmailThreadId) {
        return threadReplyStatusRepository
                .findByGmailThreadId(gmailThreadId)
                .flatMap(threadReplyStatus -> Optional.ofNullable(threadReplyStatus.getDraftId()));
    }

    @ApplicationModuleListener
    void on(MailOutboundObserved event) {
        classify(
                new ThreadReplyClassificationInput(
                        event.tenantId(),
                        event.gmailThreadId(),
                        event.gmailMessageId(),
                        true,
                        true,
                        false,
                        null,
                        false));
    }

    private ThreadReplyStatus classifyWithTenantBound(
            ThreadReplyClassificationInput classificationInput) {
        Optional<ThreadReplyStatusEntity> existingStatus =
                threadReplyStatusRepository.findByGmailThreadId(
                        classificationInput.gmailThreadId());
        if (existingStatus
                .map(ThreadReplyStatusEntity::getLastClassifiedMessageId)
                .filter(classificationInput.lastMessageId()::equals)
                .isPresent()) {
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
        if (existingStatus.isPresent()) {
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
            ThreadReplyClassificationInput classificationInput) {
        if (classificationTransaction == null) {
            return classifyWithTenantBound(classificationInput);
        }
        ThreadReplyStatus classifiedStatus =
                classificationTransaction.execute(
                        _ -> classifyWithTenantBound(classificationInput));
        return Objects.requireNonNull(classifiedStatus, "classifiedStatus must not be null");
    }

    private static TransactionTemplate classificationTransaction(
            PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private static ThreadReplyBucket bucketFor(ThreadReplyClassificationInput classificationInput) {
        if (classificationInput.lastMessageFromIsTenant()
                && classificationInput.threadHasSentLabel()
                && !classificationInput.lastMessageIsAutoReply()) {
            return ThreadReplyBucket.AWAITING_THEIR_REPLY;
        }
        return ThreadReplyBucket.TO_REPLY;
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
