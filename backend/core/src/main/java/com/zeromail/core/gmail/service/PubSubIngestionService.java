package com.zeromail.core.gmail.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.tenant.TenantContext;

/**
 * Owns all persistence for the ack-fast Pub/Sub push path.
 *
 * <p>Controllers never inject repositories; this service is the boundary for unscoped
 * tenant lookup plus tenant-bound pubsub_delivery INSERT.
 */
@Service
public class PubSubIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PubSubIngestionService.class);

    private final JdbcTemplate jdbc;
    private final PubSubDeliveryRepository deliveryRepository;
    private final TransactionTemplate tx;

    public PubSubIngestionService(JdbcTemplate jdbc,
                                  PubSubDeliveryRepository deliveryRepository,
                                  PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.deliveryRepository = deliveryRepository;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Ack-fast ingestion.
     *
     * <p>Tenant lookup is intentionally unscoped native SQL because Gmail email lookup
     * happens before a tenant is known. The tenant-bound INSERT transaction opens only
     * after TenantContext is bound, preserving the Hibernate tenant invariant.
     */
    public IngestResult ingestPushEnvelope(String emailAddress,
                                           String pubsubMessageId,
                                           long historyId,
                                           String sanitizedPayload) {
        List<UUID> tenantIds = jdbc.query(
                """
                SELECT tenant_id
                FROM gmail_connections
                WHERE LOWER(google_email) = ?
                  AND status = 'CONNECTED'
                LIMIT 1
                """,
                (rs, _) -> rs.getObject("tenant_id", UUID.class),
                emailAddress.toLowerCase(Locale.ROOT));

        if (tenantIds.isEmpty()) {
            log.info("event=pubsub_unknown_email_dropped");
            return IngestResult.UNKNOWN_EMAIL;
        }

        UUID tenantId = tenantIds.getFirst();
        AtomicReference<IngestResult> result = new AtomicReference<>();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() ->
                result.set(tx.execute(_ -> {
                    int inserted = deliveryRepository.insertPendingIfAbsent(
                            UUID.randomUUID(),
                            tenantId,
                            pubsubMessageId,
                            historyId,
                            sanitizedPayload);
                    if (inserted == 0) {
                        log.info("event=pubsub_duplicate_delivery_dropped tenantId={}", tenantId);
                        return IngestResult.DUPLICATE;
                    }
                    log.info("event=pubsub_delivery_accepted tenantId={}", tenantId);
                    return IngestResult.ACCEPTED;
                })));
        return result.get();
    }
}
