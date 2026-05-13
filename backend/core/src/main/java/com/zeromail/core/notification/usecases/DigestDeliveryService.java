package com.zeromail.core.notification.usecases;

import com.zeromail.core.notification.persistence.DigestDeliveryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigestDeliveryService {

    private final DigestDeliveryRepository digestDeliveryRepository;

    public DigestDeliveryService(DigestDeliveryRepository digestDeliveryRepository) {
        this.digestDeliveryRepository = digestDeliveryRepository;
    }

    @Transactional
    public void deleteForTenant(UUID tenantId) {
        digestDeliveryRepository.deleteByTenantId(tenantId);
    }
}
