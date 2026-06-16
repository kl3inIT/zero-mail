package com.zeromail.api.security;

import com.zeromail.api.security.events.GmailConnectionRevokedEvent;
import com.zeromail.api.security.events.OAuth2TokenRefreshFailed;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GmailAccessGuard {

    private final GmailConnectionRepository connectionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public GmailAccessGuard(
            GmailConnectionRepository connectionRepository,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.connectionRepository = connectionRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Invariant: the {@code ScopedValue} must be bound BEFORE the JPA transaction opens, so
     * Hibernate's {@code CurrentTenantIdentifierResolver} captures the real tenant when the session
     * is created (rather than the bootstrap sentinel from {@link
     * com.zeromail.core.tenant.ScopedValueTenantResolver#BOOTSTRAP_TENANT}). For that reason this
     * method does not use {@code @Transactional} on itself — it would open the transaction before
     * our wrap. Instead it binds the ScopedValue, then uses TransactionTemplate inside the bound
     * scope.
     */
    @EventListener
    public void on(OAuth2TokenRefreshFailed event) {
        if (!"invalid_grant".equals(event.errorCode())) return;
        UUID tenant;
        try {
            tenant = UUID.fromString(event.tenantId());
        } catch (IllegalArgumentException invalidTenantIdException) {
            return;
        }
        UUID gmailConnectionId;
        try {
            gmailConnectionId = UUID.fromString(event.gmailConnectionId());
        } catch (IllegalArgumentException | NullPointerException invalidMailboxIdException) {
            return;
        }
        MailboxRef mailboxRef = new MailboxRef(tenant, gmailConnectionId);
        ScopedValue.where(TenantContext.TENANT, tenant.toString())
                .run(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        _ ->
                                                connectionRepository
                                                        .findByIdAndTenantId(
                                                                mailboxRef.gmailConnectionId(),
                                                                mailboxRef.tenantId())
                                                        .ifPresent(
                                                                connection -> {
                                                                    connection.setStatus(
                                                                            GmailConnectionStatus
                                                                                    .DISCONNECTED);
                                                                    connection.setDisconnectedAt(
                                                                            Instant.now());
                                                                    connectionRepository.save(
                                                                            connection);
                                                                    eventPublisher.publishEvent(
                                                                            new GmailConnectionRevokedEvent(
                                                                                    mailboxRef
                                                                                            .tenantId(),
                                                                                    Instant.now()));
                                                                })));
    }
}
