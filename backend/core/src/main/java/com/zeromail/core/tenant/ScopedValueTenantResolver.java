package com.zeromail.core.tenant;

import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

@Component
public class ScopedValueTenantResolver
        implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    /**
     * Sentinel returned during application bootstrap or for non-request operations (e.g., Spring
     * Data JPA query validation) when no tenant is bound. Hibernate uses this for {@code @TenantId}
     * filters; because no real tenant ever uses this UUID, any data accidentally written or read
     * against it is naturally isolated. Real request paths bind a real tenant via
     * TenantBindingFilter before any persistence call, so this sentinel only appears outside the
     * request lifecycle.
     */
    public static final UUID BOOTSTRAP_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.currentOptional().map(UUID::fromString).orElse(BOOTSTRAP_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
