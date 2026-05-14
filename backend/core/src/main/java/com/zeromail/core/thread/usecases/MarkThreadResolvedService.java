package com.zeromail.core.thread.usecases;

import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.thread.persistence.ThreadReplyStatusRepository;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MarkThreadResolvedService {

    private static final Logger log = LoggerFactory.getLogger(MarkThreadResolvedService.class);

    private final ThreadReplyStatusRepository threadReplyStatusRepository;
    private final TransactionOperations transactionOperations;

    @Autowired
    public MarkThreadResolvedService(
            ThreadReplyStatusRepository threadReplyStatusRepository,
            PlatformTransactionManager transactionManager) {
        this(threadReplyStatusRepository, new TransactionTemplate(transactionManager));
    }

    MarkThreadResolvedService(
            ThreadReplyStatusRepository threadReplyStatusRepository,
            TransactionOperations transactionOperations) {
        this.threadReplyStatusRepository =
                Objects.requireNonNull(
                        threadReplyStatusRepository,
                        "threadReplyStatusRepository must not be null");
        this.transactionOperations =
                Objects.requireNonNull(
                        transactionOperations, "transactionOperations must not be null");
    }

    public void markResolved(UUID tenantId, String gmailThreadId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        String threadId = requireText(gmailThreadId, "gmailThreadId");
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                transactionOperations.executeWithoutResult(
                                        _ ->
                                                threadReplyStatusRepository
                                                        .findByGmailThreadId(threadId)
                                                        .ifPresent(
                                                                status -> {
                                                                    status.setResolved(true);
                                                                    threadReplyStatusRepository
                                                                            .save(status);
                                                                    log.info(
                                                                            "event=thread_marked_resolved tenantId={} gmailThreadId={}",
                                                                            tenantId,
                                                                            threadId);
                                                                })));
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}
