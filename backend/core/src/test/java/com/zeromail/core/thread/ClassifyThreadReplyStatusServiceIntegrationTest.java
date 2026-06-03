package com.zeromail.core.thread;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.gmail.event.MailOutboundObserved;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.thread.domain.ThreadReplyBucket;
import com.zeromail.core.thread.domain.ThreadReplyStatus;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.thread.usecases.ThreadReplyClassificationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("SqlResolve")
class ClassifyThreadReplyStatusServiceIntegrationTest extends PostgresContainerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000005c2");

    @Autowired ClassifyThreadReplyStatusService classifyThreadReplyStatusService;

    @Autowired JdbcTemplate jdbcTemplate;

    @PersistenceContext EntityManager entityManager;

    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void classify_binds_tenant_before_repository_access() {
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?) on conflict (id) do nothing",
                TENANT_ID,
                "thread-reply-classifier");

        ThreadReplyStatus result =
                classifyThreadReplyStatusService.classify(
                        new ThreadReplyClassificationInput(
                                TENANT_ID,
                                "gmail-thread-classifier",
                                "gmail-message-classifier",
                                true,
                                true,
                                false,
                                null,
                                false,
                                false));

        String storedBucket =
                jdbcTemplate.queryForObject(
                        """
                        select bucket
                        from thread_reply_status
                        where tenant_id = ? and gmail_thread_id = ?
                        """,
                        String.class,
                        TENANT_ID,
                        "gmail-thread-classifier");
        assertThat(result.bucket()).isEqualTo(ThreadReplyBucket.AWAITING_THEIR_REPLY);
        assertThat(storedBucket).isEqualTo("AWAITING_THEIR_REPLY");

        jdbcTemplate.update("delete from tenants where id = ?", TENANT_ID);
    }

    @Test
    void classify_opens_tenant_bound_transaction_when_outer_listener_transaction_exists() {
        UUID listenerTenantId = UUID.fromString("00000000-0000-0000-0000-0000000005c3");
        String gmailThreadId = "gmail-thread-listener-transaction";
        ClassifyThreadReplyStatusService classifierTarget =
                AopTestUtils.getTargetObject(classifyThreadReplyStatusService);
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?) on conflict (id) do nothing",
                listenerTenantId,
                "thread-reply-listener");

        TransactionTemplate listenerTransaction = new TransactionTemplate(transactionManager);
        listenerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        listenerTransaction.executeWithoutResult(
                _ -> {
                    entityManager
                            .createQuery(
                                    "select count(status) from ThreadReplyStatusEntity status",
                                    Long.class)
                            .getSingleResult();
                    classifierTarget.on(
                            new MailOutboundObserved(
                                    listenerTenantId,
                                    gmailThreadId,
                                    "gmail-message-listener-transaction",
                                    Instant.parse("2026-05-30T00:00:00Z")));
                });

        String storedBucket =
                jdbcTemplate.queryForObject(
                        """
                        select bucket
                        from thread_reply_status
                        where tenant_id = ? and gmail_thread_id = ?
                        """,
                        String.class,
                        listenerTenantId,
                        gmailThreadId);
        assertThat(storedBucket).isEqualTo("AWAITING_THEIR_REPLY");

        jdbcTemplate.update("delete from tenants where id = ?", listenerTenantId);
    }
}
