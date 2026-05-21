package com.zeromail.core.gmail.usecases;

import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.gmail.persistence.lowlevel.PubSubTenantLookupRepository;
import com.zeromail.core.tenant.TenantContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns all persistence orchestration for the ack-fast Pub/Sub push path.
 *
 * <p>Controllers never inject repositories; this service is the boundary for the unscoped tenant
 * lookup plus the tenant-bound pubsub_delivery INSERT.
 */
@Service
public class PubSubIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PubSubIngestionService.class);

    private final PubSubTenantLookupRepository tenantLookupRepository;
    private final PubSubDeliveryRepository deliveryRepository;
    private final TransactionTemplate transactionTemplate;

    public PubSubIngestionService(
            PubSubTenantLookupRepository tenantLookupRepository,
            PubSubDeliveryRepository deliveryRepository,
            PlatformTransactionManager transactionManager) {
        this.tenantLookupRepository =
                Objects.requireNonNull(
                        tenantLookupRepository, "tenantLookupRepository must not be null");
        this.deliveryRepository =
                Objects.requireNonNull(deliveryRepository, "deliveryRepository must not be null");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(
                                transactionManager, "transactionManager must not be null"));
    }

    /**
     * Ack-fast ingestion.
     *
     * <p>Tenant lookup is intentionally unscoped because Gmail email lookup happens before a tenant
     * is known. The tenant-bound INSERT transaction opens only after TenantContext is bound,
     * preserving the Hibernate tenant invariant.
     */
    public IngestResult ingestPushEnvelope(
            String emailAddress, String pubsubMessageId, long historyId, String sanitizedPayload) {
        Optional<UUID> tenantIdLookup =
                tenantLookupRepository.findConnectedTenantIdByEmail(emailAddress);
        if (tenantIdLookup.isEmpty()) {
            log.info("event=pubsub_unknown_email_dropped");
            return IngestResult.UNKNOWN_EMAIL;
        }

        UUID tenantId = tenantIdLookup.get();
        AtomicReference<IngestResult> result = new AtomicReference<>();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                result.set(
                                        transactionTemplate.execute(
                                                _ -> {
                                                    int insertedCount =
                                                            deliveryRepository
                                                                    .insertPendingIfAbsent(
                                                                            UUID.randomUUID(),
                                                                            tenantId,
                                                                            pubsubMessageId,
                                                                            historyId,
                                                                            sanitizedPayload);
                                                    if (insertedCount == 0) {
                                                        log.info(
                                                                "event=pubsub_duplicate_delivery_dropped tenantId={}",
                                                                tenantId);
                                                        return IngestResult.DUPLICATE;
                                                    }
                                                    log.info(
                                                            "event=pubsub_delivery_accepted tenantId={}",
                                                            tenantId);
                                                    return IngestResult.ACCEPTED;
                                                })));
        return result.get();
    }
}
